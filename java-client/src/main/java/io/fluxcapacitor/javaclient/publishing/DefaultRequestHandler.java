package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static io.fluxcapacitor.javaclient.common.ClientUtils.waitForResults;
import static java.lang.String.format;

@AllArgsConstructor
@Slf4j
public class DefaultRequestHandler implements RequestHandler {

    private final TrackingClient trackingClient;
    private final Serializer serializer;
    private final String clientName;
    private final String clientId;
    private final Map<Integer, CompletableFuture<Message>> callbacks = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger();
    private final AtomicBoolean started = new AtomicBoolean();

    @Override
    public CompletableFuture<Message> sendRequest(SerializedMessage request,
                                                  Consumer<SerializedMessage> requestSender) {
        if (started.compareAndSet(false, true)) {
            TrackingUtils.start(format("%s_RESULT", clientName), trackingClient, this::handleMessages);
        }
        CompletableFuture<Message> result = new CompletableFuture<>();
        int requestId = nextId.getAndIncrement();
        callbacks.put(requestId, result);
        request.setRequestId(requestId);
        request.setSource(clientId);
        requestSender.accept(request);
        return result;
    }

    @Override
    public void close() {
        waitForResults(Duration.ofSeconds(2), callbacks.values());
    }

    protected void handleMessages(List<SerializedMessage> messages) {
        messages.forEach(m -> {
            try {
                CompletableFuture<Message> future = callbacks.get(m.getRequestId());
                if (future == null) {
                    log.warn("Received response with index {} for unknown request {}", m.getIndex(), m.getRequestId());
                    return;
                }
                Object result;
                try {
                    result = serializer.deserialize(m.getData());
                } catch (Exception e) {
                    log.error("Failed to deserialize result with id {}. Continuing with next result", m.getRequestId(), e);
                    future.completeExceptionally(e);
                    return;
                }
                try {
                    if (result instanceof Throwable) {
                        future.completeExceptionally((Exception) result);
                    } else {
                        future.complete(new Message(result, m.getMetadata()));
                    }
                } catch (Exception e) {
                    log.error("Failed to complete request with id {}", m.getRequestId(), e);
                }
            } finally {
                callbacks.remove(m.getRequestId());
            }
        });
    }
}

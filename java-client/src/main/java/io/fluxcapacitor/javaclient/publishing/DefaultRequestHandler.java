package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.configuration.client.ClientProperties;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static io.fluxcapacitor.common.MessageType.RESULT;
import static io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration.DEFAULT_CONSUMER_NAME;

@AllArgsConstructor
@Slf4j
public class DefaultRequestHandler implements RequestHandler {

    private final TrackingClient trackingClient;
    private final ClientProperties properties;
    private final Map<Integer, CompletableFuture<SerializedMessage>> callbacks = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger();
    private final AtomicBoolean started = new AtomicBoolean();

    @Override
    public CompletableFuture<SerializedMessage> sendRequest(SerializedMessage request,
                                                            Consumer<SerializedMessage> requestSender) {
        if (started.compareAndSet(false, true)) {
            TrackingUtils.start(DEFAULT_CONSUMER_NAME.apply(RESULT), trackingClient, this::handleMessages);
        }
        CompletableFuture<SerializedMessage> result = new CompletableFuture<>();
        int requestId = nextId.getAndIncrement();
        callbacks.put(requestId, result);
        request.setRequestId(requestId);
        request.setSource(properties.getClientId());
        requestSender.accept(request);
        return result;
    }

    protected void handleMessages(List<SerializedMessage> messages) {
        messages.forEach(m -> {
            CompletableFuture<SerializedMessage> future = callbacks.remove(m.getRequestId());
            if (future == null) {
                log.warn("Received response with index {} for unknown request {}", m.getIndex(), m.getRequestId());
                return;
            }
            try {
                future.complete(m);
            } catch (Exception e) {
                log.error("Failed to complete request with id {}", m.getRequestId(), e);
            }
        });
    }
}

package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.publishing.correlation.DefaultCorrelationDataProvider;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.client.DefaultTracker;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentCorrelationData;
import static java.net.http.HttpRequest.BodyPublishers.ofByteArray;

@Slf4j
public class ForwardingWebConsumer implements AutoCloseable {
    private final String host;
    private final LocalServerConfig localServerConfig;
    private final ConsumerConfiguration configuration;
    private final HttpClient httpClient;
    private final AtomicReference<Registration> registration = new AtomicReference<>();

    public ForwardingWebConsumer(LocalServerConfig localServerConfig, ConsumerConfiguration configuration) {
        this.host = "http://localhost:" + localServerConfig.getPort();
        this.localServerConfig = localServerConfig;
        this.configuration = configuration;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Synchronized
    public void start(FluxCapacitor fluxCapacitor) {
        GatewayClient gatewayClient = fluxCapacitor.client().getGatewayClient(MessageType.WEBRESPONSE);
        BiConsumer<SerializedMessage, SerializedMessage> gateway = (request, response) -> {
            response.setTarget(request.getSource());
            response.setRequestId(request.getRequestId());
            response.setMetadata(response.getMetadata().with(currentCorrelationData()));
            gatewayClient.send(Guarantee.NONE, response);
        };
        Consumer<List<SerializedMessage>> consumer = messages -> messages.forEach(m -> {
            Map<String, String> correlationData = getCorrelationData(m);
            try {
                HttpRequest request = createRequest(m);
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                        .whenComplete((r, e) -> {
                            if (e == null && r.statusCode() == 404 && localServerConfig.isIgnore404()) {
                                return;
                            }
                            gateway.accept(m, e == null ? toMessage(r, correlationData) : toMessage(e, correlationData));
                        });
            } catch (Exception e) {
                try {
                    gateway.accept(m, toMessage(e, correlationData));
                } catch (Exception e2) {
                    log.error("Failed to create response message from exception", e2);
                }
            }
        });
        registration.getAndUpdate(r -> r == null ? DefaultTracker.start(consumer, MessageType.WEBREQUEST,
                                                                        configuration, fluxCapacitor) : r);
    }

    protected Map<String, String> getCorrelationData(SerializedMessage m) {
        try {
            return FluxCapacitor.getOptionally().map(FluxCapacitor::correlationDataProvider).orElse(
                    DefaultCorrelationDataProvider.INSTANCE).getCorrelationData(new DeserializingMessage(
                            m, type -> null, MessageType.WEBRESPONSE));
        } catch (Exception e) {
            log.error("Failed to get correlation data for request message", e);
            return Collections.emptyMap();
        }
    }

    protected HttpRequest createRequest(SerializedMessage m) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(new URI(host + WebRequest.getUrl(m.getMetadata())))
                    .method(WebRequest.getMethod(m.getMetadata()).name(),
                            m.getData().getValue().length == 0 ? HttpRequest.BodyPublishers.noBody() :
                                    ofByteArray(m.getData().getValue()));

            String[] headers = WebRequest.getHeaders(m.getMetadata()).entrySet().stream()
                    .filter(e -> !isRestricted(e.getKey()))
                    .flatMap(e -> e.getValue().stream().flatMap(v -> Stream.of(e.getKey(), v)))
                    .toArray(String[]::new);
            if (headers.length > 0) {
                builder.headers(headers);
            }
            if (m.getData().getFormat() != null) {
                builder.header("Content-Type", m.getData().getFormat());
            }
            return builder.build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create HttpRequest", e);
        }
    }

    protected boolean isRestricted(String headerName) {
        return Set.of("connection", "content-length", "expect", "host", "upgrade").contains(headerName.toLowerCase());
    }

    protected SerializedMessage toMessage(HttpResponse<byte[]> response,
                                          Map<String, String> correlationData) {
        HttpHeaders headers = response.headers();
        Metadata metadata = Metadata.of(correlationData)
                .with(WebResponse.asMetadata(response.statusCode(), headers.map()));
        return new SerializedMessage(new Data<>(response.body(), null, 0,
                                                headers.firstValue("content-type").orElse(null)), metadata,
                                     FluxCapacitor.generateId(), System.currentTimeMillis());
    }

    protected SerializedMessage toMessage(Throwable error,
                                          Map<String, String> correlationData) {
        log.error("Failed to handle web request: " + error.getMessage() + ". Continuing with next request.", error);
        return new SerializedMessage(
                new Data<>("The request failed due to a server error".getBytes(), null, 0, "text/plain"),
                Metadata.of(correlationData).with(WebResponse.asMetadata(500, Collections.emptyMap())),
                FluxCapacitor.generateId(), System.currentTimeMillis());
    }

    @Override
    public void close() {
        registration.getAndUpdate(r -> {
            if (r != null) {
                r.cancel();
            }
            return null;
        });
    }
}

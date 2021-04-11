package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.client.DefaultTracker;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.client.jaxrs.ClientHttpEngine;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.ClientHttpEngineBuilder43;
import org.jboss.resteasy.plugins.interceptors.GZIPDecodingInterceptor;
import org.jboss.resteasy.plugins.interceptors.GZIPEncodingInterceptor;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.client.ClientBuilder.newBuilder;

@Slf4j
public class DefaultWebClient implements AutoCloseable {

    ResteasyClient httpClient = httpClient();
    Registration registration;
    private final ExecutorService executorService = Executors.newWorkStealingPool(8);

    //TODO remove
    static byte[] NULL = "null".getBytes();

    public DefaultWebClient(Integer port, ConsumerConfiguration consumerConfiguration, Client client, ResultGateway resultGateway) {
        Consumer<List<SerializedMessage>> consumer = messages -> messages.forEach(m -> {
            try {
                MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
                WebRequest.getHeaders(m.getMetadata()).forEach(headers::addAll);
                String method = ofNullable(WebRequest.getMethod(m.getMetadata())).filter(WebMethod::isHttp)
                        .map(h -> h == WebMethod.UNKNOWN ? m.getMetadata().get("method") : h.name()).orElse(null);
                if (method == null) {
                    return;
                }
                WebTarget target = httpClient.target(format("http://localhost:%s", port));
                MediaType mediaType = determineMediaType(m.getData().getFormat(), (String) headers.getFirst("Content-Type"));

                Future<Response> future = target.path(WebRequest.getPath(m.getMetadata()))
                        .request().headers(headers).async().method(method, Entity.entity(
                                Arrays.equals(NULL, m.getData().getValue()) ? null : m.getData().getValue(), mediaType));

                executorService.submit(() -> {
                    try {
                        try (Response response = future.get()) {
                            resultGateway.respond(new WebResponse(response.readEntity(byte[].class),
                                    response.getStatus(), response.getStringHeaders()), m.getSource(), m.getRequestId());
                        }
                    } catch (Exception e) {
                        log.error(format("Could not deliver webrequest to webserver on port %s. %s", port, e.getMessage()), e);
                        resultGateway.respond(WebUtils.unexpectedError(), m.getSource(), m.getRequestId());
                    }
                });

            } catch (Exception e) {
                log.error(format("Could not create webrequest to webserver on port %s. %s", port, e.getMessage()), e);
                resultGateway.respond(WebUtils.unexpectedError(), m.getSource(), m.getRequestId());
            }
        });
        registration = DefaultTracker.start(consumer, consumerConfiguration, client);
    }

    protected MediaType determineMediaType(String format, String contentType) {
        try {
            return MediaType.valueOf(format);
        } catch (Exception e) {
            try {
                return MediaType.valueOf(contentType);
            } catch (Exception e1) {
                log.warn(format("Could not determine webrequest mediatype from format %s or contentType %s. Defaulting to text/plain", format, contentType));
                return MediaType.TEXT_PLAIN_TYPE;
            }
        }
    }

    @Override
    public void close() throws Exception {
        if (registration != null) {
            registration.cancel();
        }
        executorService.shutdown();
        httpClient.close();
    }


    private static ResteasyClient httpClient(UnaryOperator<ClientBuilder> configurator) {
        ResteasyClientBuilder builder = (ResteasyClientBuilder) configurator.apply(
                newBuilder().register(GZIPEncodingInterceptor.class)
                        .register(new GZIPDecodingInterceptor(Integer.MAX_VALUE)));
        ClientHttpEngine httpEngine = new ClientHttpEngineBuilder43().resteasyClientBuilder(builder).build();
        return builder.httpEngine(httpEngine).build();
    }

    private static ResteasyClient httpClient() {
        return httpClient(UnaryOperator.identity());
    }
}

package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
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
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static io.fluxcapacitor.common.MessageType.WEBREQUEST;
import static java.lang.String.format;
import static javax.ws.rs.client.ClientBuilder.newBuilder;

@Slf4j
public class DefaultWebClient implements AutoCloseable {

    ResteasyClient httpClient = httpClient();
    Registration registration;

    public DefaultWebClient(String port, ConsumerConfiguration consumerConfiguration, Client client, ResultGateway resultGateway, Serializer serializer) {
        Consumer<List<SerializedMessage>> consumer = messages ->
                serializer.deserializeMessages(messages.stream(), false, WEBREQUEST).forEach(m -> {
                    try {
                        WebRequest webRequest = (WebRequest) m.toMessage();
                        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
                        webRequest.getHeaders().forEach(headers::addAll);
                        WebTarget target = httpClient.target(format("http://localhost:%s", port));
                        Invocation.Builder requestBuilder = target.path(webRequest.getPath()).request().headers(headers);

                        try (Response response = requestBuilder.build(webRequest.getMethod(),
                                Entity.entity(webRequest.getPayload(), MediaType.valueOf((String) headers.getFirst("Content-Type")))).invoke()) {

                            resultGateway.respond(new WebResponse(response.readEntity(byte[].class),
                                            response.getStatus(), response.getStringHeaders()),
                                    m.getSerializedObject().getSource(), m.getSerializedObject().getRequestId());
                        }
                    } catch (Exception e) {
                        log.error(format("Could not deliver webrequest to webserver on port %s. %s", port, e.getMessage()), e);
                        resultGateway.respond(WebUtils.unexpectedError(),
                                m.getSerializedObject().getSource(), m.getSerializedObject().getRequestId());
                    }
                });
        registration = DefaultTracker.start(consumer, consumerConfiguration, client);
    }

    @Override
    public void close() throws Exception {
        if (registration != null) {
            registration.cancel();
        }
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

package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.undertow.Undertow;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@Path("")
@Slf4j
public class WebRequestForwardingTest extends Application {

    private static final UndertowJaxrsServer server = new UndertowJaxrsServer();
    private static final AtomicInteger port = new AtomicInteger(9000);

    @BeforeAll
    @SneakyThrows
    static void beforeAll() {
        server.deploy(WebRequestForwardingTest.class);
        server.start(Undertow.builder().addHttpListener(port.get(), "0.0.0.0"));
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    private final TestFixture testFixture = TestFixture.createAsync(
            DefaultFluxCapacitor.builder().forwardWebRequestsToLocalServer(port.get()));

    @Test
    void testGet() {
        testFixture.whenWebRequest(WebRequest.builder().method(HttpRequestMethod.GET).path("/get").build())
                .expectThat(fc -> verify(fc.client().getGatewayClient(MessageType.WEBRESPONSE)).send(any(), any()))
                .expectResult("get".getBytes());
    }

    @Test
    void testPostString() {
        testFixture.whenWebRequest(WebRequest.builder().method(HttpRequestMethod.POST).path("/string").payload("test").build())
                .expectThat(fc -> verify(fc.client().getGatewayClient(MessageType.WEBRESPONSE)).send(
                        any(), ArgumentMatchers.<SerializedMessage>argThat(message -> "200".equals(message.getMetadata().get("status")))))
                .expectResult("test".getBytes());
    }

    @Test
    void testPostObject() {
        testFixture.whenWebRequest(WebRequest.builder().method(HttpRequestMethod.POST).path("/object").payload(new Foo("bar")).build())
                .expectThat(fc -> verify(fc.client().getGatewayClient(MessageType.WEBRESPONSE)).send(
                        any(), ArgumentMatchers.<SerializedMessage>argThat(message -> "200".equals(message.getMetadata().get("status")))))
                .expectResult("object".getBytes());
    }

    @GET
    @Path("/get")
    public String get() {
        return "get";
    }

    @POST
    @Path("/string")
    public String post(String payload) {
        return payload;
    }

    @POST
    @Path("/object")
    @Consumes(APPLICATION_JSON)
    public String postObject(Foo payload) {
        return "object";
    }

    @Override
    public Set<Object> getSingletons() {
        return Set.of(this, new JsonBodyReader());
    }

    @Value
    private static class Foo {
        String bar;
    }

    @Provider
    @Consumes(APPLICATION_JSON)
    private static  class JsonBodyReader implements MessageBodyReader<Object> {

        @Override
        public boolean isReadable(Class type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return true;
        }

        @Override
        public Object readFrom(Class<Object> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                               MultivaluedMap<String, String> httpHeaders,
                               InputStream entityStream) throws IOException, WebApplicationException {
            try (entityStream) {
               return JacksonSerializer.defaultObjectMapper.readValue(entityStream.readAllBytes(), type);
            }
        }
    }
}


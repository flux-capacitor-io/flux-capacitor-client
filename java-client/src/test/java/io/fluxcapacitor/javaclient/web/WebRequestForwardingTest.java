package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.TestUtils;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.undertow.Undertow;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyReader;
import jakarta.ws.rs.ext.Provider;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Set;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@Path("")
@Slf4j
public class WebRequestForwardingTest extends Application {

    private static final UndertowJaxrsServer server = new UndertowJaxrsServer();
    private static final int port = TestUtils.getAvailablePort();

    @BeforeAll
    @SneakyThrows
    static void beforeAll() {
        server.deploy(WebRequestForwardingTest.class);
        server.start(Undertow.builder().addHttpListener(port, "0.0.0.0"));
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    private final TestFixture testFixture = TestFixture.createAsync(
            DefaultFluxCapacitor.builder().forwardWebRequestsToLocalServer(port));

    @Test
    void testGet() {
        testFixture.whenWebRequest(WebRequest.builder().method(HttpRequestMethod.GET).url("/get").build())
                .expectThat(fc -> verify(fc.client().getGatewayClient(MessageType.WEBRESPONSE)).send(any(), any()))
                .expectResult("get".getBytes());
    }

    @Test
    void testPostString() {
        testFixture.whenWebRequest(WebRequest.builder().method(HttpRequestMethod.POST).url("/string").payload("test").build())
                .expectThat(fc -> verify(fc.client().getGatewayClient(MessageType.WEBRESPONSE)).send(
                        any(), ArgumentMatchers.<SerializedMessage>argThat(message -> "200".equals(message.getMetadata().get("status")))))
                .expectResult("test".getBytes());
    }

    @Test
    void testPostObject() {
        testFixture.whenWebRequest(WebRequest.builder().method(HttpRequestMethod.POST).url("/object").payload(new Foo("bar")).build())
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


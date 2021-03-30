package io.fluxcapacitor.javaclient.web;

import com.fasterxml.jackson.databind.type.MapType;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthenticatedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthorizedException;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationException;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static io.fluxcapacitor.common.api.Metadata.objectMapper;
import static java.util.Collections.singletonMap;

public class WebUtils {
    public static MapType headersJavaType = objectMapper.getTypeFactory().constructMapType(Map.class,
            objectMapper.getTypeFactory().constructType(String.class),
            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));

    public static BiFunction<Object, Throwable, WebResponse> defaultWebResponseFormatter() {
        return (result, e) -> {
            if (e != null) {
                if (e instanceof ValidationException || e instanceof SerializationException) {
                    return error(e.getMessage() , 400);
                }
                if (e instanceof UnauthenticatedException || e instanceof UnauthorizedException) {
                    return error(e.getMessage() , 401);
                }
                if (e instanceof FunctionalException) {
                    return error(e.getMessage() , 403);
                }
                return unexpectedError();
            }
            if (result instanceof WebResponse) {
                return (WebResponse) result;
            }
            if (result instanceof Message) {
                Message m = (Message) result;
                m = m.getMetadata().get("status") != null ? (Message) result : m.withMetadata(m.getMetadata().with("status",
                        m.getPayload() == null ? 204 : 200));
                return new WebResponse(m.getPayload(), m.getMetadata(), m.getMessageId(), m.getTimestamp());
            }
            return new WebResponse(result, result == null ? 204 : 200);
        };
    }

    public static WebResponse error(String message, int status) {
        return new WebResponse(singletonMap("error", message), status);
    }

    public static WebResponse unexpectedError() {
        return new WebResponse(singletonMap("error", "An unexpected error occurred"), 500);
    }

}

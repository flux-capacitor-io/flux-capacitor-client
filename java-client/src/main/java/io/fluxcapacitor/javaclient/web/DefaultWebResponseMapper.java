package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthenticatedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthorizedException;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static java.util.stream.Collectors.toMap;

public class DefaultWebResponseMapper implements WebResponseMapper {
    @Override
    public WebResponse map(Object payload, Metadata metadata) {
        WebResponse.Builder builder = WebResponse.builder().headers(asHeaders(metadata));
        var status = Optional.ofNullable(WebResponse.getStatusCode(metadata));
        if (payload instanceof Throwable) {
            if (payload instanceof ValidationException || payload instanceof SerializationException) {
                builder.status(status.orElse(400));
                builder.payload(((Exception) payload).getMessage());
            } else if (payload instanceof UnauthorizedException || payload instanceof UnauthenticatedException) {
                builder.status(status.orElse(401));
                builder.payload(((Exception) payload).getMessage());
            } else if (payload instanceof FunctionalException) {
                builder.status(status.orElse(403));
                builder.payload(((Exception) payload).getMessage());
            } else if (payload instanceof TimeoutException
                    || payload instanceof io.fluxcapacitor.javaclient.publishing.TimeoutException) {
                builder.status(status.orElse(503));
                builder.payload("The request has timed out. Please try again later.");
            } else {
                builder.status(status.orElse(500));
                builder.payload("An unexpected error occurred.");
            }
        } else {
            builder.status(status.orElse(payload == null ? 204 : 200));
            builder.payload(payload);
        }
        return builder.build();
    }

    private Map<String, List<String>> asHeaders(Metadata metadata) {
        return metadata.entrySet().stream().collect(toMap(Map.Entry::getKey, e -> List.of(e.getValue())));
    }
}

package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthenticatedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthorizedException;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationException;

import java.util.concurrent.TimeoutException;

public class DefaultWebResponseMapper implements WebResponseMapper {
    @Override
    public WebResponse map(Object payload, Metadata metadata) {
        WebResponse.Builder builder = WebResponse.builder();
        if (payload instanceof Throwable) {
            if (payload instanceof ValidationException || payload instanceof SerializationException) {
                builder.status(400);
                builder.payload(((Exception) payload).getMessage());
            } else if (payload instanceof UnauthorizedException || payload instanceof UnauthenticatedException) {
                builder.status(401);
                builder.payload(((Exception) payload).getMessage());
            } else if (payload instanceof FunctionalException) {
                builder.status(403);
                builder.payload(((Exception) payload).getMessage());
            } else if (payload instanceof TimeoutException
                    || payload instanceof io.fluxcapacitor.javaclient.publishing.TimeoutException) {
                builder.status(503);
                builder.payload("The request has timed out. Please try again later.");
            } else {
                builder.status(500);
                builder.payload("An unexpected error occurred.");
            }
        } else {
            builder.status(payload == null ? 204 : 200);
            builder.payload(payload);
        }
        return builder.build().addMetadata(metadata);
    }
}

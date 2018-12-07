package io.fluxcapacitor.javaclient.tracking.handling.errorreporting;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import io.fluxcapacitor.javaclient.common.exception.TechnicalException;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.ErrorGateway;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;

import java.util.function.Function;

import static java.lang.String.format;

@AllArgsConstructor
public class ErrorReportingInterceptor implements HandlerInterceptor {

    private final ErrorGateway errorGateway;

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    Handler<DeserializingMessage> handler,
                                                                    String consumer) {
        return m -> {
            try {
                return function.apply(m);
            } catch (FunctionalException | TechnicalException e) {
                reportError(e, m);
                throw e;
            } catch (Exception e) {
                reportError(new TechnicalException(format("Handler %s failed to handle a %s", handler, m)), m);
                throw e;
            }
        };
    }

    protected void reportError(Exception e, DeserializingMessage cause) {
        errorGateway.report(new Message(e, MessageType.ERROR), cause.getSerializedObject().getSource());
    }
}

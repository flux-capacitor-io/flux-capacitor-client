package io.fluxcapacitor.javaclient.tracking.handling.errorreporting;

import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import io.fluxcapacitor.javaclient.common.exception.TechnicalException;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.ErrorGateway;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static java.lang.String.format;

@AllArgsConstructor
public class ErrorReportingInterceptor implements HandlerInterceptor {

    private final ErrorGateway errorGateway;

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    Handler<DeserializingMessage> handler,
                                                                    String consumer) {
        return message -> {
            try {
                Object result = function.apply(message);
                if (result instanceof CompletionStage<?>) {
                    ((CompletionStage<?>) result).whenComplete((r, e) -> {
                        if (e != null) {
                            message.run(m -> reportError(e, handler, m));
                        }
                    });
                }
                return result;
            } catch (Exception e) {
                reportError(e, handler, message);
                throw e;
            }
        };
    }

    protected void reportError(Throwable e, Handler<DeserializingMessage> handler, DeserializingMessage cause) {
        if (!(e instanceof FunctionalException || e instanceof TechnicalException)) {
            e = new TechnicalException(format("Handler %s failed to handle a %s", handler, cause));
        }
        errorGateway.report(new Message(e, cause.getMetadata()), cause.getSerializedObject().getSource());
    }
}

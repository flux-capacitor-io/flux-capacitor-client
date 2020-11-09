/*
 * Copyright (c) 2016-2020 Flux Capacitor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.javaclient.tracking.handling.errorreporting;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import io.fluxcapacitor.javaclient.common.exception.TechnicalException;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.ErrorGateway;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.exception.ExceptionUtils;

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
        Metadata metadata = cause.getMetadata();
        if (!(e instanceof FunctionalException || e instanceof TechnicalException)) {
            metadata = metadata.with("stackTrace", ExceptionUtils.getStackTrace(e));
            e = new TechnicalException(format("Handler %s failed to handle a %s", handler, cause));
        }
        errorGateway.report(new Message(e, metadata), cause.getSerializedObject().getSource());
    }
}

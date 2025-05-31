/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
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
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import io.fluxcapacitor.javaclient.common.exception.TechnicalException;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.ErrorGateway;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.unwrapException;
import static io.fluxcapacitor.javaclient.common.ClientUtils.isLocalHandler;
import static java.lang.String.format;

/**
 * {@link HandlerInterceptor} that reports exceptions to the configured {@link ErrorGateway}.
 * <p>
 * This interceptor captures any uncaught exceptions thrown during message handling and appends them to the error log.
 * It also supports reporting errors from asynchronous {@link CompletionStage} results returned by handler methods.
 * <p>
 * Local handler invocations (typically in-process invocations marked as {@code @LocalHandler}) are excluded from error reporting.
 *
 * <h2>Behavior</h2>
 * <ul>
 *     <li>If the handler returns a {@link CompletionStage}, any exception occurring during its completion is reported.</li>
 *     <li>If the handler throws an exception synchronously, it is reported immediately and rethrown.</li>
 *     <li>Exceptions marked as {@link FunctionalException} or {@link TechnicalException} are passed through as-is.</li>
 *     <li>Other exceptions are wrapped in a {@link TechnicalException} and enriched with stack trace metadata.</li>
 * </ul>
 *
 * @see ErrorGateway
 * @see HandlerInterceptor
 * @see TechnicalException
 * @see FunctionalException
 */
@AllArgsConstructor
@Slf4j
public class ErrorReportingInterceptor implements HandlerInterceptor {

    private final ErrorGateway errorGateway;

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    HandlerInvoker invoker) {
        return message -> {
            if (isLocalHandler(invoker, message)) {
                return function.apply(message);
            }
            try {
                Object result = function.apply(message);
                if (result instanceof CompletionStage<?> s) {
                    s.whenComplete((r, e) -> {
                        if (e != null) {
                            message.run(m -> reportError(e, invoker, m));
                        }
                    });
                }
                return result;
            } catch (Throwable e) {
                reportError(e, invoker, message);
                throw e;
            }
        };
    }

    protected void reportError(Throwable e, HandlerInvoker invoker, DeserializingMessage cause) {
        e = unwrapException(e);
        Metadata metadata = cause.getMetadata();
        if (!(e instanceof FunctionalException || e instanceof TechnicalException)) {
            metadata = metadata.with("stackTrace", ExceptionUtils.getStackTrace(e));
            e = new TechnicalException(format("Handler %s failed to handle a %s", invoker.getTargetClass(), cause));
        }
        errorGateway.report(new Message(e, metadata));
    }
}

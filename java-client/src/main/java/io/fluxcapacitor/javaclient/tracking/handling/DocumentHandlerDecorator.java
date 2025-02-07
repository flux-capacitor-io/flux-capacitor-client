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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.modeling.SearchParameters;
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import lombok.AllArgsConstructor;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.fluxcapacitor.javaclient.common.ClientUtils.getSearchParameters;
import static io.fluxcapacitor.javaclient.common.ClientUtils.memoize;

@AllArgsConstructor
public class DocumentHandlerDecorator implements HandlerDecorator {
    static final Function<Executable, Optional<String>> collectionSupplier = memoize(m -> ReflectionUtils.<HandleDocument>
                    getMethodAnnotation(m, HandleDocument.class).map(a -> ClientUtils.getTopic(a, m)));

    private final Supplier<DocumentStore> documentStoreSupplier;

    @Override
    public Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler) {
        return new DocumentHandler(handler);
    }

    @AllArgsConstructor
    protected class DocumentHandler implements Handler<DeserializingMessage> {

        private final Handler<DeserializingMessage> delegate;

        @Override
        public Optional<HandlerInvoker> getInvoker(DeserializingMessage message) {
            return delegate.getInvoker(message)
                    .flatMap(i -> !i.isPassive() && i.getMethod() instanceof Method m
                                  && m.getReturnType().isAssignableFrom(message.getPayloadClass())
                            ? collectionSupplier.apply(i.getMethod())
                            .map(topic -> new DocumentHandlerInvoker(i, topic, message)) : Optional.of(i));
        }

        @Override
        public Class<?> getTargetClass() {
            return delegate.getTargetClass();
        }

        protected class DocumentHandlerInvoker extends HandlerInvoker.DelegatingHandlerInvoker {
            private final DeserializingMessage message;
            private final String collection;

            public DocumentHandlerInvoker(HandlerInvoker delegate, String collection, DeserializingMessage message) {
                super(delegate);
                this.message = message;
                this.collection = collection;
            }

            @Override
            public Object invoke(BiFunction<Object, Object, Object> combiner) {
                Object result = delegate.invoke(combiner);
                handleResult(result);
                return result;
            }

            private void handleResult(Object result) {
                DocumentStore store = documentStoreSupplier.get();
                if (result == null) {
                    store.deleteDocument(message.getMessageId(), collection);
                } else {
                    if (ClientUtils.getRevisionNumber(result) > message.getSerializedObject().getRevision()) {
                        if (getSearchParameters(result.getClass()) instanceof SearchParameters searchParams
                            && (searchParams.getTimestampPath() != null || searchParams.getEndPath() != null)) {
                            store.index(result, message.getMessageId(), collection);
                        } else {
                            var start = Optional.ofNullable(message.getMetadata().get("$start")).map(Long::valueOf)
                                    .map(Instant::ofEpochMilli).orElse(null);
                            var end = Optional.ofNullable(message.getMetadata().get("$end")).map(Long::valueOf)
                                    .map(Instant::ofEpochMilli).orElse(null);
                            store.index(result, message.getMessageId(), collection, start, end);
                        }
                    }
                }
            }
        }
    }
}

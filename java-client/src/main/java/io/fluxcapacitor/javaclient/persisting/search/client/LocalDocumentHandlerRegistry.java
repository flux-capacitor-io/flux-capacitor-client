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

package io.fluxcapacitor.javaclient.persisting.search.client;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.handling.HandlerFilter;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerRegistry;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A handler registry implementation intended for local testing and development that registers handlers for document
 * updates in a specific collection.
 *
 * @see InMemorySearchStore
 * @see HandlerRegistry
 */
@AllArgsConstructor
public class LocalDocumentHandlerRegistry implements HasLocalHandlers {
    private final InMemorySearchStore searchStore;
    @Delegate
    private final HandlerRegistry handlerRegistry;
    private final DispatchInterceptor dispatchInterceptor;
    private final Serializer serializer;

    private final AtomicBoolean initialized = new AtomicBoolean();

    @Override
    public Registration registerHandler(Object target, HandlerFilter handlerFilter) {
        if (initialized.compareAndSet(false, true)) {
            searchStore.registerMonitor((collection, messages) -> serializer.deserializeMessages(
                    messages.stream(), MessageType.DOCUMENT, collection).forEach(message -> {
                dispatchInterceptor.monitorDispatch(message.toMessage(), MessageType.DOCUMENT, collection);
                handle(message);
            }));
        }
        return handlerRegistry.registerHandler(target, handlerFilter);
    }
}

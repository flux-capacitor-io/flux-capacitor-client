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

package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;

import static java.util.Optional.ofNullable;

/**
 * Mechanism that enables modification of a message before it is dispatched to local handlers or Flux Capacitor. Also
 * can be used to monitor outgoing messages or block message publication altogether.
 */
@FunctionalInterface
public interface DispatchInterceptor {

    DispatchInterceptor noOp = (m, messageType) -> m;


    /**
     * Intercepts the publication of a message. Implementers can use this to modify the contents of a message or block
     * the publication altogether.
     * <p>
     * Return {@code null} or throw an exception to prevent publication of the message.
     */
    Message interceptDispatch(Message message, MessageType messageType);

    /**
     * Enables modification of the {@link SerializedMessage} before it is published.
     * <p>
     * This method is invoked by message gateways right before publication of a message (so right after
     * {@link #monitorDispatch} is invoked).
     * <p>
     * Although message publication is stopped when {@code null} is returned or an exception is thrown, it is preferable
     * to use {@link #interceptDispatch} for that.
     */
    default SerializedMessage modifySerializedMessage(SerializedMessage serializedMessage,
                                                      Message message, MessageType messageType) {
        return serializedMessage;
    }

    /**
     * Monitors the dispatch of a message after all dispatch interceptors have had a change to stop or modify the
     * message. Don't use this method to prevent message handling or publication.
     * <p>
     * This method is invoked by message gateways right before local handling of a message and optional publication.
     */
    default void monitorDispatch(Message message, MessageType messageType) {
        //no op
    }

    default DispatchInterceptor andThen(DispatchInterceptor nextInterceptor) {
        return new DispatchInterceptor() {
            @Override
            public Message interceptDispatch(Message m, MessageType t) {
                return ofNullable(DispatchInterceptor.this.interceptDispatch(m, t))
                        .map(message -> nextInterceptor.interceptDispatch(message, t)).orElse(null);
            }

            @Override
            public void monitorDispatch(Message message, MessageType messageType) {
                DispatchInterceptor.this.monitorDispatch(message, messageType);
                nextInterceptor.monitorDispatch(message, messageType);
            }

            @Override
            public SerializedMessage modifySerializedMessage(SerializedMessage s, Message m,
                                                             MessageType type) {
                return ofNullable(DispatchInterceptor.this.modifySerializedMessage(s, m, type))
                        .map(message -> nextInterceptor.modifySerializedMessage(message, m, type)).orElse(null);
            }
        };
    }
}

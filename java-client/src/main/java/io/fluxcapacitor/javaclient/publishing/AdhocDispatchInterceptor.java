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
import lombok.SneakyThrows;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

public class AdhocDispatchInterceptor implements DispatchInterceptor {

    private static final ThreadLocal<Map<MessageType, DispatchInterceptor>> delegates = new ThreadLocal<>();

    public static Optional<? extends DispatchInterceptor> getAdhocInterceptor(MessageType messageType) {
        return Optional.ofNullable(delegates.get()).map(map -> map.get(messageType));
    }

    @SneakyThrows
    public static <T> T runWithAdhocInterceptor(Callable<T> task, DispatchInterceptor adhocInterceptor,
                                                MessageType... messageTypes) {
        Map<MessageType, DispatchInterceptor> previous = delegates.get();
        Map<MessageType, DispatchInterceptor> merged = Optional.ofNullable(previous).orElseGet(HashMap::new);
        Stream<MessageType> typeStream =
                (messageTypes.length == 0 ? EnumSet.allOf(MessageType.class).stream() : Arrays.stream(messageTypes));
        typeStream.forEach(messageType -> merged.compute(
                messageType, (t, i) -> i == null ? adhocInterceptor : i.andThen(adhocInterceptor)));
        try {
            delegates.set(merged);
            return task.call();
        } finally {
            delegates.set(previous);
        }
    }

    public static void runWithAdhocInterceptor(Runnable task, DispatchInterceptor adhocInterceptor,
                                               MessageType... messageTypes) {
        runWithAdhocInterceptor(() -> {
            task.run();
            return null;
        }, adhocInterceptor, messageTypes);
    }

    @Override
    public Message interceptDispatch(Message message, MessageType messageType) {
        var adhocInterceptor = getAdhocInterceptor(messageType);
        return adhocInterceptor.isPresent() ? adhocInterceptor.get().interceptDispatch(message, messageType) : message;
    }

    @Override
    public SerializedMessage modifySerializedMessage(SerializedMessage serializedMessage, Message message,
                                                     MessageType messageType) {
        var adhocInterceptor = getAdhocInterceptor(messageType);
        return adhocInterceptor.isPresent() ?
                adhocInterceptor.get().modifySerializedMessage(serializedMessage, message, messageType) :
                serializedMessage;
    }
}

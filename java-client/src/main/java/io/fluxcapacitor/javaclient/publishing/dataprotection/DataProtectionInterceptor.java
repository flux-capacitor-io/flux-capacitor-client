/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.publishing.dataprotection;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.keyvalue.KeyValueStore;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedFields;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.readProperty;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.writeProperty;

@AllArgsConstructor
@Slf4j
public class DataProtectionInterceptor implements DispatchInterceptor, HandlerInterceptor {

    public static String METADATA_KEY = "$protectedData";

    private final KeyValueStore keyValueStore;
    private final Serializer serializer;

    @Override
    @SuppressWarnings("unchecked")
    public Function<Message, SerializedMessage> interceptDispatch(Function<Message, SerializedMessage> function,
                                                                  MessageType messageType) {
        return m -> {
            Map<String, String> protectedFields = new HashMap<>();
            if (m.getMetadata().containsKey(METADATA_KEY)) {
                protectedFields.putAll(m.getMetadata().get(METADATA_KEY, Map.class));
            } else {
                Object payload = m.getPayload();
                getAnnotatedFields(m.getPayload(), ProtectData.class).forEach(
                        field -> readProperty(field.getName(), payload).ifPresent(value -> {
                            String key = FluxCapacitor.generateId();
                            keyValueStore.store(key, value, Guarantee.STORED);
                            protectedFields.put(field.getName(), key);
                        }));
                if (!protectedFields.isEmpty()) {
                    m = m.withMetadata(m.getMetadata().with(METADATA_KEY, protectedFields));
                }
            }
            if (!protectedFields.isEmpty()) {
                Object payloadCopy = serializer.deserialize(serializer.serialize(m.getPayload()));
                protectedFields.forEach((name, key) -> writeProperty(name, payloadCopy, null));
                m = m.withPayload(payloadCopy);
            }
            return function.apply(m);
        };
    }

    @Override
    @SuppressWarnings("unchecked")
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    Handler<DeserializingMessage> handler,
                                                                    String consumer) {
        return m -> {
            if (m.getMetadata().containsKey(METADATA_KEY)) {
                Object payload = m.getPayload();
                Map<String, String> protectedFields = m.getMetadata().get(METADATA_KEY, Map.class);
                boolean dropProtectedData = handler.getMethod(m).isAnnotationPresent(DropProtectedData.class);
                protectedFields.forEach((fieldName, key) -> {
                    try {
                        writeProperty(fieldName, payload, keyValueStore.get(key));
                    } catch (Exception e) {
                        log.warn("Failed to set field {}", fieldName, e);
                    }
                    if (dropProtectedData) {
                        keyValueStore.delete(key);
                    }
                });
            }
            return function.apply(m);
        };
    }
}

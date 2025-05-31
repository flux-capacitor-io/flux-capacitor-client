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

package io.fluxcapacitor.javaclient.publishing.dataprotection;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.handling.HandlerInvoker;
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

/**
 * A {@link DispatchInterceptor} and {@link HandlerInterceptor} that supports secure transmission of sensitive data
 * fields by removing them from the payload before dispatch and restoring them during handling.
 *
 * <p>This interceptor works in two phases:
 * <ul>
 *   <li><strong>Dispatch phase:</strong>
 *     <ul>
 *       <li>Scans the payload for fields annotated with {@link ProtectData}.</li>
 *       <li>Each such field is serialized and stored securely in a designated store, indexed by a generated key.</li>
 *       <li>The payload is cloned and sensitive fields are removed (set to {@code null}).</li>
 *       <li>The generated key references are stored in the message metadata under {@link #METADATA_KEY}.</li>
 *     </ul>
 *   </li>
 *   <li><strong>Handling phase:</strong>
 *     <ul>
 *       <li>Looks for protected data references in the message metadata.</li>
 *       <li>If present, retrieves the original values from the store using the stored keys.</li>
 *       <li>Injects the retrieved values back into the message payload before invocation.</li>
 *       <li>If the target method is annotated with {@link DropProtectedData}, the stored entries are deleted after injection.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>This strategy is useful for preventing sensitive or private data from being persisted to the message log,
 * while still allowing handlers to receive full context during execution.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public class MyHandler {
 *   @HandleCommand
 *   public void handle(@ProtectData String ssn, ...) {
 *     ...
 *   }
 *
 *   @HandleCommand
 *   @DropProtectedData
 *   public void auditSensitive(@ProtectData String secretField, ...) {
 *     ...
 *     // After invocation, the secret is permanently removed
 *   }
 * }
 * }
 * </pre>
 *
 * <p>Note: The payload is cloned via (de)serialization to ensure the original object remains unmodified.
 *
 * @see ProtectData
 * @see DropProtectedData
 * @see DispatchInterceptor
 * @see HandlerInterceptor
 */
@AllArgsConstructor
@Slf4j
public class DataProtectionInterceptor implements DispatchInterceptor, HandlerInterceptor {

    public static String METADATA_KEY = "$protectedData";

    private final KeyValueStore keyValueStore;
    private final Serializer serializer;

    @Override
    @SuppressWarnings("unchecked")
    public Message interceptDispatch(Message m, MessageType messageType, String topic) {
        Map<String, String> protectedFields = new HashMap<>();
        if (m.getMetadata().containsKey(METADATA_KEY)) {
            protectedFields.putAll(m.getMetadata().get(METADATA_KEY, Map.class));
        } else {
            Object payload = m.getPayload();
            getAnnotatedFields(payload, ProtectData.class).forEach(
                    field -> readProperty(field.getName(), payload).ifPresent(value -> {
                        String key = FluxCapacitor.currentIdentityProvider().nextTechnicalId();
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
        return m;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    HandlerInvoker invoker) {
        return m -> {
            if (m.getMetadata().containsKey(METADATA_KEY)) {
                Object payload = m.getPayload();
                Map<String, String> protectedFields = m.getMetadata().get(METADATA_KEY, Map.class);
                boolean dropProtectedData = invoker.getMethod().isAnnotationPresent(DropProtectedData.class);
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

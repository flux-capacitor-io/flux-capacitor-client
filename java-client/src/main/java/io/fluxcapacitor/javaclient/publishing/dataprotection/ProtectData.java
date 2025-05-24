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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field within a message payload as containing sensitive information that should be protected.
 * <p>
 * When a field is annotated with {@code @ProtectData}, the value of the field is automatically offloaded and stored
 * separately from the main payload during serialization. This ensures that sensitive data (e.g. social security numbers,
 * access tokens, secret keys, etc.) is not persisted or exposed together with the rest of the message payload.
 * <p>
 * When a message is later deserialized and passed to a handler, Flux will automatically reinject the protected
 * information into the payload prior to invoking the handler method.
 * <p>
 * To permanently remove protected data after it is no longer needed, consider using the {@link DropProtectedData}
 * annotation on a handler method.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public class RegisterCitizen {
 *
 *     String name;
 *
 *     @ProtectData
 *     String socialSecurityNumber;
 * }
 * }</pre>
 *
 * @see DropProtectedData
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ProtectData {
}

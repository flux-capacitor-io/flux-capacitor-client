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
 * Indicates that a message handler method or constructor is the final usage point for protected data.
 * <p>
 * When a handler is annotated with {@code @DropProtectedData}, any {@link ProtectData}-annotated fields in the
 * payload will be injected for this invocation. After the handler method completes, the protected data will be
 * permanently deleted from persistence.
 * <p>
 * This annotation is useful to ensure that sensitive data does not linger longer than needed, satisfying
 * data minimization principles and privacy requirements (e.g. GDPR).
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @HandleEvent
 * @DropProtectedData
 * void handle(RegisterCitizen event) {
 *     // Use sensitive data for validation or audit
 *     audit(event.getSocialSecurityNumber());
 *     // After this handler, the socialSecurityNumber is permanently removed from storage
 * }
 * }</pre>
 *
 * @see ProtectData
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.RUNTIME)
public @interface DropProtectedData {
}

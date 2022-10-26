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

package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.javaclient.common.Message;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation on methods that intercept events before they are applied to an entity (and before any @AssertLegal methods
 * are invoked).
 * <p>
 * Methods may return {@code null} or {@code void} to indicate that the event should not be applied. Alternatively,
 * a {@link java.util.stream.Stream}, {@link java.util.Collection} or {@link java.util.Optional} may be returned to map
 * the event into 0 or more (different) events. Any other value that's returned will be applied instead of the input
 * event.
 * <p>
 * Annotated methods may consist of any number of parameters. If any parameter type is assignable to the loaded
 * aggregate type or any matching child entities it will be injected. Other parameters like event {@link Message} will
 * be automatically injected.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InterceptApply {
}

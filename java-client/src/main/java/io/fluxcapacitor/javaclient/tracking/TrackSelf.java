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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a message payload class can handle itself as a message handler.
 * <p>
 * When this annotation is present on a class (or its interface), Flux will automatically register a handler for any
 * implementation of that type. The resulting handler behaves like a standard external consumer: the message is first
 * published to the appropriate message log and then tracked and processed via the configured consumer infrastructure.
 * </p>
 *
 * <p>
 * Without this annotation, a payload class with handler methods (e.g., {@link HandleCommand}) would be treated as a
 * local handler. That means it would be executed immediately, bypassing the message log and any tracking.
 * </p>
 *
 * <p>
 * You may also annotate the class with {@link Consumer} to configure message consumption in an isolated consumer.
 * If no {@code @Consumer} annotation is present, the handler will be assigned to the application's default consumer.
 * </p>
 *
 * <p>
 * <strong>Spring integration:</strong> When using Spring, types annotated with {@code @TrackSelf} will be
 * automatically
 * detected and registered as handlers via component scanning. When not using Spring, or when testing using an
 * asynchronous {@code TestFixture}, you must register the class manually.
 * </p>
 *
 * @see Consumer on how to configure message consumption in an isolated consumer.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@Documented
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public @interface TrackSelf {
}
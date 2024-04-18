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
 * Annotation to be placed on the (interface of) a message payload class with handler methods, such as
 * {@link HandleCommand @HandleCommand}.
 * <p>
 * If this annotation is present a handler will automatically be created for (implementations of) the annotated type.
 * The handler will behave like any standalone, non-local handler. Note that if this annotation would not be present,
 * the payload class would function as local handler.
 * <p>
 * You can optionally add a {@link Consumer @Consumer} annotation to consume and handle the message in an isolated
 * process. If the Consumer annotation is not present, the handler will be registered with the default message consumer
 * for the current application.
 * <p>
 * If Spring is used, annotated types will be automatically registered as handlers during a component scan. When Spring
 * isn't used, or when testing using an async TestFixtures, make sure to register the class manually as a handler.
 *
 * @see Consumer
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@Documented
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public @interface TrackSelf {
}

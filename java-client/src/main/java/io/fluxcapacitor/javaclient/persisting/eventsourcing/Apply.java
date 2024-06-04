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

package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.modeling.EventPublication;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be placed on methods that modify an entity value. The methods can be declared in either entity or
 * event. The annotation can also be placed on a constructor of the entity or static method if the event creates a new
 * entity.
 * <p>
 * Annotated  methods are also used if an entity is loaded using event sourcing.
 * <p>
 * Annotated methods may consist of any number of parameters. If any parameter type is assignable to the loaded
 * aggregate type or any matching child entities it will be injected. Other parameters like event {@link Message} will
 * be automatically injected.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Apply {
    EventPublication eventPublication() default EventPublication.DEFAULT;
}

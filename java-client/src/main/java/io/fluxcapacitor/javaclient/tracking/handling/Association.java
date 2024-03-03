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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.search.SearchInclude;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to associate a message to a stored View. If a message is associated with one or several Views, the
 * Views will be fetched and the message will be applied on all Views.
 * <p>
 * This annotation can be added to properties (fields and getters) of the View class, or on handler methods of the View.
 * If placed on a property of the View, the name of the property will be used to associate the event, unless another
 * property name is specified using {@link #value()}. If the annotation is used on a handler method,
 * {@link #value()} is required to match on a property by that name in the message payload.
 *
 * @see View
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SearchInclude
public @interface Association {
    /**
     * Returns names of properties in the message payload to associate with. If the annotation is placed on a
     * property of the View this may be left empty to associate using the name of the View property.
     */
    String[] value() default {};
}

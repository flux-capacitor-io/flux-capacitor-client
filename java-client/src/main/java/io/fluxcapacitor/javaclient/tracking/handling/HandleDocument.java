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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.persisting.search.Searchable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used on handler methods or constructors that handle search document messages in a given collection.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@HandleMessage(MessageType.DOCUMENT)
public @interface HandleDocument {
    /**
     * Optional parameter to specify the collection name. If set, the class returned in {@link #documentClass()}
     * is ignored.
     */
    String value() default "";

    /**
     * Optional parameter to specify the class of the documents to be handled. If the class is annotated with {@link Searchable} or has an
     * annotation that itself is annotated with {@link Searchable}, the annotation will be used to determine the
     * document collection, otherwise the simple name of the class will be used as document collection.
     * <p>
     * If {@link #value()} is specified the returned class here will be ignored.
     *
     * @see Searchable
     */
    Class<?> documentClass() default Void.class;

    boolean disabled() default false;
}

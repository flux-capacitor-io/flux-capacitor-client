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

package io.fluxcapacitor.common.search;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation that indicates that the property is to be ignored when indexing a document for search. When a
 * property is ignored, the document can't be matched using this property.
 * <p>
 * Note that the property is not lost when the document is serialized or deserialized. If that is the intention, make
 * the property transient instead, e.g. using an annotation like {@link java.beans.Transient} or
 * {@link com.fasterxml.jackson.annotation.JsonIgnore}.
 * <p>
 * Subclasses can re-enable indexing by specifying a {@link #value()} of {@code false} on the overridden property.
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SearchIgnore {
    /**
     * Optional argument that defines whether this annotation is active ({@code true}) or not ({@code false}).
     */
    boolean value() default true;
}

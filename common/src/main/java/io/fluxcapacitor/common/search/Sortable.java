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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation on fields and methods that indicates that a property is to be used as facet when indexing a
 * document for search.
 * <p>
 * Facets can be used to easily categorize documents and quickly get statistics about a set of matching documents. This
 * is particularly useful when a collection contains many documents and statistics about those documents are required
 * frequently.
 * <p>
 * The facet value is determined as follows:
 * <p>
 * 1) in case the object is null or a blank string the facet is ignored;
 * <p>
 * 2) in case the object is a collection, facets are created for each of the collection elements;
 * <p>
 * 3) in case the object is a map, facets are created for each of the map values. Keys of the map are appended to the
 * facet name (including a delimiting slash);
 * <p>
 * 4) in case the class of the element is annotated with {@link Sortable}, the toString() value of the element is used;
 * <p>
 * 5) in case the object is a constant value (number, string or boolean), the toString() value of the element is used;
 * <p>
 * 6) for other values, nested facets are collected by inspecting all annotated properties of the object. If the object
 * contains any facets these will be included as well. Names of nested facets will be appended to the parent facet name
 * (including a delimiting slash);
 * <p>
 * 7) in other cases the toString() value of the element is used.
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Sortable {
    /**
     * Optional argument that defines the name of the facet. If left empty, the property name will be used.
     */
    String value() default "";
}

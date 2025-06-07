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

import io.fluxcapacitor.common.api.search.constraints.BetweenConstraint;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation on fields and getters that indicates that a property is to be used for sorting and filtering.
 * <p>
 * Use this annotation if you want to filter documents fast using a {@link BetweenConstraint} or when you want to sort
 * documents in a collection on something else than the default document time range.
 * <p>
 * The index value is determined as follows:
 * <p>
 * 1) in case the object is null or a blank string the index is ignored;
 * <p>
 * 2) in case the object is a collection, an index is created for the <strong>maximum</strong> value of the collection
 * elements (use getter if you need one for the minimum value too);
 * <p>
 * 3) in case the object is a map, facets are created for each of the map values. Keys of the map are appended to the
 * property name (including a delimiting slash);
 * <p>
 * 4) in case the class of the element is annotated with {@link Sortable}, the toString() value of the element is used;
 * <p>
 * 5) in case the object is a constant value (number, string or boolean) the element is used as is;
 * <p>
 * 6) for other values, nested indexes are collected by inspecting all annotated properties of the object. If the object
 * contains any indexed values these will be included as well. Names of nested indexes will be appended to the parent
 * property name (including a delimiting slash);
 * <p>
 * 7) in other cases the toString() value of the element is used.
 * <p>
 * Note that placing this annotation on a property while there are already documents in the collection, will not
 * automatically make sure the existing documents get retroactively indexed. See {@code @HandleDocument} if you need
 * that.
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface Sortable {
    /**
     * Optional argument that defines the name of the indexed property. If left empty, the property name will be used.
     */
    String value() default "";
}

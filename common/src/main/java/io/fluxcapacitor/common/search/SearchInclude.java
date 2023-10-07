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
 * Marker annotation that indicates that a property is to be included when indexing a document for search. When a
 * property is included, the document can be matched using this property. Note that inclusion is the default for
 * properties, so this annotation is only useful to override the effects of {@link SearchExclude}(true) on a parent or
 * ancestor.
 * <p>
 * When this annotation is present on a type, all properties of the class will be included when indexing, unless they
 * are individually annotated with {@link SearchExclude}(true).
 * <p>
 * Note that this annotation is an alias for {@link SearchExclude}(false) and has the same effect.
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SearchExclude(false)
public @interface SearchInclude {
}

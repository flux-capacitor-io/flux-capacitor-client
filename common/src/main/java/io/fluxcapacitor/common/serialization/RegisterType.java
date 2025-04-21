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

package io.fluxcapacitor.common.serialization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a type or package that should be registered in the {@link TypeRegistry}.
 * <p>
 * If the marked type is a package, all types in the package and its ancestor packages will be registered.
 * <p>
 * Registered types can be referenced by their simple type (e.g.: Foo) when used as input in TestFixtures or when
 * deserializing using {@link JsonUtils}. If multiple classes share the same simple name, it is also possible to pass
 * the last part of the fully qualified class name (e.g.: example.Foo) to select a class.
 * <p>
 * To reference a type in a JSON string use the {@code @class} attribute. For example: {@code {"@class": "Foo"}}.
 *
 * @see TypeRegistry
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Inherited
public @interface RegisterType {
    /**
     * Specifies regular expression values found in the types that should be matched. A type is only matched if this
     * array is empty or if one or more of the specified regular expression values can be found in the type name.
     */
    String[] contains() default {};
}

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
 * Annotation to register a class or package for inclusion in the {@link TypeRegistry}.
 * <p>
 * Registered types are used to enable simplified type resolution, for example when deserializing JSON that references a
 * type by name or when using {@code TestFixture} helpers to specify input/output types.
 * <p>
 * If this annotation is placed on a <strong>class</strong>, that class will be registered in the {@code TypeRegistry}.
 * If placed on a <strong>package</strong>, all types in that package and its ancestor packages will be registered.
 * <p>
 * Types or packages marked with {@code @RegisterType} are discovered and indexed during annotation processing. This
 * means that they must be available on the classpath at compile time, and annotation processing must be enabled for the
 * type registry to function correctly.
 *
 * <h2>Usage</h2>
 * Registered types can be referenced by:
 * <ul>
 *   <li>Simple class name (e.g., {@code "Foo"})</li>
 *   <li>Disambiguated name using trailing segments (e.g., {@code "example.Foo"})</li>
 * </ul>
 * <p>
 * This is especially useful when a type is referenced in serialized form using the {@code "@class"} attribute:
 * <pre>{@code
 * {
 *   "@class": "Foo",
 *   "name": "Example"
 * }
 * }</pre>
 * <p>
 * If multiple classes have the same simple name, Flux will attempt to resolve the type using the shortest suffix
 * that still uniquely identifies it (e.g., {@code "billing.Foo"} vs. {@code "shipping.Foo"}). If conflicts remain,
 * the returned type is unpredictable.
 *
 * <h2>Filtering using {@link #contains()}</h2>
 * You can restrict which types are registered by specifying patterns to match against the class name:
 * <pre>{@code
 * @RegisterType(contains = {"Dto", "Request"})
 * }</pre>
 * This ensures that only classes whose names include {@code "Dto"} or {@code "Request"} will be registered.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Inherited
public @interface RegisterType {

    /**
     * Optional filters to determine which types should be registered based on name matching.
     * <p>
     * If this array is left empty (the default), all types in the annotated class or package are included. If provided,
     * a type is only registered if one or more of these regular expressions match the class name.
     *
     * @return array of regex patterns used to match class names for registration
     */
    String[] contains() default {};
}

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

package io.fluxcapacitor.javaclient.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a path prefix that contributes to the final URI of a web handler.
 * <p>
 * The {@code @Path} annotation can be placed on packages, classes, methods, and properties (fields or getters).
 * Path values from outer levels (e.g. packages or enclosing classes) are chained with inner levels unless a path starts
 * with {@code /}, in which case the chain is reset and the path is treated as absolute.
 * </p>
 *
 * <p>
 * If a {@code @Path} value is empty, the system will fall back to using the simple name of the latest package,
 * (e.g. {@code com.example.users} → {@code users}).
 * </p>
 *
 * <p>
 * When {@code @Path} is placed on a property (field or getter), its value is used dynamically at runtime
 * to determine the path segment. In that case, the annotation is expected to be used without an explicit value.
 * The runtime property value will follow the same chaining rules: if it starts with a slash, it resets the path.
 * </p>
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * @Path
 * package com.example.api;
 *
 * @Path("users")
 * public class UserController {
 *     @HandleGet("/{id}")
 *     WebResponse getUser(@PathParam String id) { ... }
 * }
 *
 * // Resolves to: /api/users/{id}
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE, ElementType.PACKAGE})
@Inherited
@Documented
public @interface Path {

    /**
     * Defines the URI path segment. If omitted (i.e. the value is empty), the simple name of the class or package is used.
     * If the value starts with '/', it is treated as an absolute path and resets any previously chained segments.
     */
    String value() default "";
}

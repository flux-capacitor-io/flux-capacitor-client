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
 * Declares the root URI path prefix for web handlers.
 * <p>
 * Can be applied at the package, class, or method level to compose full request paths.
 * The most specific annotation overrides more general ones (e.g. method &gt; class &gt; package).
 * </p>
 *
 * <p>
 * The path value is automatically normalized (ensuring slashes are added if necessary).
 * </p>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * @Path("/users")
 * public class UserController {
 *
 *     @HandleGet("/{id}")
 *     public User getUser(@PathParam String id) { ... }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE, ElementType.PACKAGE})
@Inherited
@Documented
public @interface Path {

    /**
     * Defines the URI path prefix to prepend to route patterns defined in handler annotations.
     */
    String value();
}

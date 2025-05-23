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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects a path variable from the URI into a handler method parameter.
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * @HandleGet("/users/{id}")
 * User getUser(@PathParam String id) { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@WebParam(type = WebParameterSource.PATH, value = "")
public @interface PathParam {
    /**
     * Parameter name in the URI pattern. If left empty, it defaults to the method parameter's name;
     */
    String value() default "";
}

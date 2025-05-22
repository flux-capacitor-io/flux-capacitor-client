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
 * Meta-annotation for parameter annotations used to inject values from an HTTP request.
 * <p>
 * Used internally by Flux to locate and bind request values to handler method parameters
 * based on the specified {@link WebParameterSource}.
 * </p>
 *
 * @see PathParam
 * @see QueryParam
 * @see HeaderParam
 * @see FormParam
 * @see CookieParam
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface WebParam {
    /**
     * Optional name of the parameter (e.g. path variable or query param).
     * If empty, defaults to the method parameter's name.
     */
    String value() default "";

    /**
     * The source of the parameter within the request (e.g. path, query, header).
     */
    WebParameterSource type();
}

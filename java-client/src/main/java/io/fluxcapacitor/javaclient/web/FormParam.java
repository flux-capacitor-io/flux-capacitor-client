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
 * Injects the value of a form field or an entire form.
 *
 * <pre>{@code
 * @HandlePost("/newsletter")
 * void handle(@FormParam String email) { }
 *
 * @HandlePost("/user")
 * UserId form(@FormParam UserData form) { }
 * }</pre>
 * <p>
 * The HTTP request must be encoded as application/x-www-form-urlencoded or multipart/form-data.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@WebParam(type = WebParameterType.FORM)
public @interface FormParam {

    /**
     * Form parameter name. If left empty, it defaults to the method parameter's name;
     */
    String value() default "";
}

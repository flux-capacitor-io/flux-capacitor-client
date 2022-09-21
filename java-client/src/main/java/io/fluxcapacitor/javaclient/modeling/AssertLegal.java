/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.modeling;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be placed on methods in commands or queries. After an aggregate is loaded and a {@link Entity}
 * is returned you can pass the command or query to the {@link Entity#assertLegal} method to assert whether or
 * not the command or query is allowed given the state of the model.
 * <p>
 * Annotated methods should contain at least one parameter. The first parameter is reserved for the Model's entity (as
 * obtained via {@link Entity#get()}).
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AssertLegal {
    int HIGHEST_PRIORITY = Integer.MAX_VALUE, LOWEST_PRIORITY = Integer.MIN_VALUE, DEFAULT_PRIORITY = 0;

    /**
     * Determines the order of assertions if there are multiple annotated methods. A method with higher priority will be
     * invoked before methods with a lower priority. Use {@link #HIGHEST_PRIORITY} to ensure that the check is performed
     * first.
     */
    int priority() default DEFAULT_PRIORITY;

    /**
     * Determines if the legality check should be performed immediately (the default), or when the current handler is
     * done, i.e. just before the aggregate updates are committed.
     */
    boolean afterHandler() default false;
}

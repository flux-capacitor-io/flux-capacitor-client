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

package io.fluxcapacitor.javaclient.common.serialization.casting;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation that indicates a method performs a revision-based type transformation.
 * <p>
 * This annotation is used internally to mark casting methods — either upcasters or downcasters —
 * and encodes the change in revision level.
 * </p>
 *
 * <ul>
 *   <li>A positive {@code revisionDelta} indicates an upcast (e.g. {@code +1})</li>
 *   <li>A negative {@code revisionDelta} indicates a downcast (e.g. {@code -1})</li>
 * </ul>
 *
 * @see Upcast
 * @see Downcast
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Cast {

    /**
     * The relative revision change this cast represents.
     * <p>
     * Positive for upcasts, negative for downcasts.
     */
    int revisionDelta();
}

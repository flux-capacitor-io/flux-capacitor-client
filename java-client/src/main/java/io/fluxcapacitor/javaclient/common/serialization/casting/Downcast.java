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

import io.fluxcapacitor.javaclient.common.serialization.Serializer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a method that transforms an object from a newer revision to an older one.
 * <p>
 * Downcasters are used to emit legacy-compatible objects (e.g. for persistence or external systems).
 * </p>
 *
 * <p>
 * Like {@link Upcast}, this annotation applies to methods that take a single argument and return a transformed object
 * of an earlier revision.
 * </p>
 *
 * <p>
 * <strong>Spring Integration:</strong> Any Spring bean containing methods annotated with {@code @Downcast}
 * is automatically discovered and registered with the serializer at startup.
 * </p>
 *
 * @see Cast
 * @see Upcast
 * @see Serializer#registerDowncasters(Object...)
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Cast(revisionDelta = -1)
public @interface Downcast {

    /**
     * The fully qualified type name this downcaster applies to (e.g., the latest version's class name).
     */
    String type();

    /**
     * The revision number of the input object this method handles.
     * <p>
     * This is the revision the method accepts and transforms from.
     */
    int revision();
}
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
 * Declares a method that transforms an object from a previous revision to a newer one.
 * <p>
 * Upcasters are invoked during deserialization when a serialized object's revision is older than the current revision.
 * They enable seamless version evolution of domain objects.
 * </p>
 *
 * <p>
 * This annotation must be placed on a method that takes a single argument (the older object) and returns the
 * transformed object. The method must be side-effect-free and deterministic.
 * </p>
 *
 * <p>
 * <strong>Spring Integration:</strong> Any Spring bean containing methods annotated with {@code @Upcast}
 * is automatically discovered and registered with the serializer at startup.
 * </p>
 *
 * @see Cast
 * @see Downcast
 * @see Serializer#registerUpcasters(Object...)
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Cast(revisionDelta = 1)
public @interface Upcast {

    /**
     * The fully qualified type name this upcaster applies to (e.g., the original serialized class name).
     */
    String type();

    /**
     * The revision number of the input type this method handles.
     * <p>
     * This is the revision the method accepts and transforms from.
     */
    int revision();
}

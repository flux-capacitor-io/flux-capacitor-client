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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the revision number of a persistable object, such as a message payload, document, or aggregate snapshot.
 * <p>
 * Revisions are used to version serialized data, allowing for upcasting migration when older versions are read from
 * storage or received from other systems.
 * <p>
 * If this annotation is not present, the revision is assumed to be {@code 0}.
 * <p>
 * The revision number is stored in the serialized {@link io.fluxcapacitor.common.api.Data} object and used to determine
 * whether an {@code Upcaster} needs to be applied during deserialization.
 * <p>
 * <p>
 * Revisions are commonly used in conjunction with:
 * <ul>
 *     <li>Message payloads sent through the event store or command log</li>
 *     <li>Serialized documents stored in the document store</li>
 *     <li>Aggregate snapshots used for efficient rehydration of event-sourced entities</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @Revision(3)
 * public class ProductDetails {
 *     String name;
 *     String description;
 * }
 * }</pre>
 * <p>
 * This indicates that the current version of the class is revision 3, and upcasters may be provided for
 * earlier revisions when deserializing older data.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface Revision {
    /**
     * The revision number of the annotated class. Defaults to {@code 0} if not explicitly set.
     *
     * @return the revision number
     */
    int value();
}

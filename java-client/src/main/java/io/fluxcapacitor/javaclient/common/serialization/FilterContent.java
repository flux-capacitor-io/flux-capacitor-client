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

package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Declares that a method should be invoked to filter the visibility of an object for a specific {@link User}.
 * <p>
 * Filtering allows objects to dynamically adjust their exposed content based on who is viewing them.
 * This is especially useful in projections, document models, or search results that may be shared with different roles.
 *
 * <p><strong>Invocation:</strong> Filtering is applied by calling {@link FluxCapacitor#filterContent(Object, User)}.
 * Filtering is <strong>not</strong> automatic; it must be triggered explicitly.</p>
 *
 * <p><strong>Injection:</strong> The annotated method may accept the following parameters:
 * <ul>
 *   <li>{@link User} – the current user performing the access</li>
 *   <li>Root object – the top-level object being filtered (useful for context when filtering nested values)</li>
 * </ul>
 * Any other arguments will be ignored.
 *
 * <p><strong>Return value:</strong> The method should return:
 * <ul>
 *   <li>{@code this} – if the value is fully visible</li>
 *   <li>a modified copy – if only a subset of the value should be shown</li>
 *   <li>{@code null} – if the value should be completely hidden</li>
 * </ul>
 *
 * <p><strong>Recursive filtering:</strong> Filtering is automatically applied to nested objects, collections, and maps.
 * When filtering results in {@code null} for an item inside a collection or map:
 * <ul>
 *   <li>The item is removed from a {@code List}</li>
 *   <li>The key-value pair is removed from a {@code Map}</li>
 * </ul>
 *
 * <p><strong>Example (filtering the object):</strong></p>
 * <pre>{@code
 * @FilterContent
 * public Order filter(User user) {
 *     return user.hasRole("admin") ? this : new Order(maskedFieldsOnly());
 * }
 * }</pre>
 *
 * <p><strong>Example (filtering a nested item with root injection):</strong></p>
 * <pre>{@code
 * @FilterContent
 * public LineItem filter(User user, Order root) {
 *     return root.isPublic() ? this : null;
 * }
 * }</pre>
 *
 * @see FluxCapacitor#filterContent(Object, User)
 * @see Serializer#filterContent(Object, User)
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FilterContent {
}

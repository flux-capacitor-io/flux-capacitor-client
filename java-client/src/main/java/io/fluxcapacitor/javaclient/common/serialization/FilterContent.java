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

import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Indicates that a method should be invoked to filter a value before it is exposed to a specific {@link User}.
 * <p>
 * This annotation is used in conjunction with {@link Serializer#filterContent(Object, User)} to dynamically
 * determine whether and how an object (or part of it) should be visible to the given user.
 * </p>
 *
 * <p>
 * The annotated method should return one of the following:
 * <ul>
 *   <li>{@code this} – if the value is fully visible to the user</li>
 *   <li>a modified copy – if only a subset of the value should be shown</li>
 *   <li>{@code null} – if the value must be hidden entirely</li>
 * </ul>
 *
 * <p>
 * Filtering is applied recursively to nested structures, including fields in collections and arrays.
 * </p>
 *
 * <p><strong>Injection:</strong> The {@link User} instance will be automatically passed to the annotated method.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @FilterContent
 * public Order filter(User user) {
 *     return user.hasRole("admin") ? this : new Order(maskedFieldsOnly());
 * }
 * }</pre>
 *
 * @see Serializer#filterContent(Object, User)
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FilterContent {
}

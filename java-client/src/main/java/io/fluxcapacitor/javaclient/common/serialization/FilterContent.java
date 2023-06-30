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
 * Annotation to be placed on methods of values that need to be filtered before they're passed to a given {@link User}.
 * <p>
 * For a value to be filtered use {@link Serializer#filterContent(Object, User)}. The user will be automatically
 * injected into annotated methods.
 * <p>
 * Both root and nested values are filtered (including if the value is in an array). You can either return the current
 * value (i.e. {@code this}) from the annotated method, or return a modified value if the user is not allowed to see all
 * properties. You can also return {@code null} to remove the value for the user entirely.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FilterContent {
}

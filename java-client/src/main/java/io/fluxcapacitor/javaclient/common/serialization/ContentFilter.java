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

/**
 * Represents an interface for filtering content before it is passed to a specified viewer. It allows for the
 * modification or transformation of the given value based on the logic implemented in the filter and considering the
 * viewer's details.
 *
 * @see FilterContent
 */
public interface ContentFilter {

    /**
     * Filters the given value based on the current viewer (user) context. If a {@link FilterContent} handler exists
     * in the object's class, it will be invoked to produce a user-specific view of the data.
     *
     * @param value  the original object
     * @param viewer the user viewing the content
     * @param <T>    the type of the content object
     * @return the filtered version of the object, or the original object if filtering failed or was not applicable
     */
    <T> T filterContent(T value, User viewer);
}

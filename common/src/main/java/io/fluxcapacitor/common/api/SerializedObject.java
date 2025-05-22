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

package io.fluxcapacitor.common.api;

public interface SerializedObject<T> {
    /**
     * Returns the serialized payload held by this object.
     *
     * @return the serialized payload
     */
    Data<T> data();

    /**
     * Returns a new {@code SerializedObject} with the given data object. If the given data is the same instance as the
     * current one, {@code this} may be returned.
     *
     * @param data the new serialized payload
     * @return a new {@code SerializedObject} instance. May be {@code this} if unchanged
     */
    SerializedObject<T> withData(Data<T> data);

    /**
     * Returns the revision of the serialized object.
     */
    default int getRevision() {
        return data().getRevision();
    }

    /**
     * Returns the type identifier for the serialized payload.
     */
    default String getType() {
        return data().getType();
    }
}

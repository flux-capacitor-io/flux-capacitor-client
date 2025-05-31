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

/**
 * Marker interface for wrapper objects that retain a reference to their originating source.
 *
 * <p>This is commonly used to wrap or decorate serialized objects (e.g., for format conversion),
 * while preserving access to the original representation. Used by infrastructure such as
 * {@link DefaultCasterChain.ConvertingSerializedObject} to maintain traceability of transformations.
 *
 * @param <T> the type of the source object being wrapped, e.g. {@link io.fluxcapacitor.common.api.SerializedMessage}.
 */
public interface HasSource<T> {

    /**
     * Returns the original source object this instance was derived from.
     *
     * @return the original source object
     */
    T getSource();
}

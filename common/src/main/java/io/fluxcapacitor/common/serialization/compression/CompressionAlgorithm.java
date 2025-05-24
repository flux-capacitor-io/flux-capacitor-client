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

package io.fluxcapacitor.common.serialization.compression;

/**
 * Enumeration of supported compression algorithms used for serializing and deserializing byte data.
 *
 * <p>The available algorithms include:
 * <ul>
 *   <li>{@link #NONE} – No compression. The input is passed through unchanged.</li>
 *   <li>{@link #LZ4} – Fast compression using the LZ4 codec. Optimized for speed and suitable for large volumes of data.</li>
 *   <li>{@link #GZIP} – Standard GZIP compression. Compatible with most zip tools and libraries.</li>
 * </ul>
 *
 * @see CompressionUtils
 */
public enum CompressionAlgorithm {
    /**
     * No compression. Data is stored and retrieved as-is.
     */
    NONE,

    /**
     * Fast compression using the LZ4 codec. Includes original size prefix in output.
     */
    LZ4,

    /**
     * GZIP compression using standard Java APIs. Produces .gz-compatible output.
     */
    GZIP
}

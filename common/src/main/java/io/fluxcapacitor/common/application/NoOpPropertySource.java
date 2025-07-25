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

package io.fluxcapacitor.common.application;

/**
 * A no-operation {@link PropertySource} implementation that never returns any property values.
 *
 * <p>This is a singleton enum used as a fallback or placeholder where a {@code PropertySource}
 * is required but no actual configuration values are available or needed.
 *
 * <p>All calls to {@link #get(String)} will return {@code null}.
 *
 * @see PropertySource
 */
public enum NoOpPropertySource implements PropertySource {
    INSTANCE;

    /**
     * Always returns {@code null} for any requested property name.
     *
     * @param name the name of the property
     * @return {@code null}
     */
    @Override
    public String get(String name) {
        return null;
    }
}

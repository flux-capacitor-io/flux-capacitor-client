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

package io.fluxcapacitor.common.tracking;

/**
 * Interface for components that expose a {@link MessageStore}.
 * <p>
 * This is used to retrieve the underlying message store from wrapping or higher-level abstractions.
 * Useful for test utilities or diagnostics that need direct access to message persistence.
 *
 * @see MessageStore
 */
public interface HasMessageStore {

    /**
     * Returns the associated {@link MessageStore} instance.
     */
    MessageStore getMessageStore();
}

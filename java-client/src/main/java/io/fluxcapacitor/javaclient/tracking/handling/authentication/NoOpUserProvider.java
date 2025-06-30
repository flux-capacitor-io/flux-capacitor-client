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

package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.HasMessage;

public enum NoOpUserProvider implements UserProvider {
    INSTANCE;

    @Override
    public User getUserById(Object userId) {
        return null;
    }

    @Override
    public User getSystemUser() {
        return null;
    }

    @Override
    public User fromMessage(HasMessage message) {
        return null;
    }

    @Override
    public boolean containsUser(Metadata metadata) {
        return false;
    }

    @Override
    public Metadata removeFromMetadata(Metadata metadata) {
        return metadata;
    }

    @Override
    public Metadata addToMetadata(Metadata metadata, User user, boolean ifAbsent) {
        return metadata;
    }
}

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
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class DelegatingUserProvider implements UserProvider {
    protected final UserProvider delegate;

    @Override
    public User getActiveUser() {
        return delegate.getActiveUser();
    }

    @Override
    public User getUserById(Object userId) {
        return delegate.getUserById(userId);
    }

    @Override
    public User getSystemUser() {
        return delegate.getSystemUser();
    }

    @Override
    public User fromMessage(HasMessage message) {
        return delegate.fromMessage(message);
    }

    @Override
    public boolean containsUser(Metadata metadata) {
        return delegate.containsUser(metadata);
    }

    @Override
    public Metadata removeFromMetadata(Metadata metadata) {
        return delegate.removeFromMetadata(metadata);
    }

    @Override
    public Metadata addToMetadata(Metadata metadata, User user, boolean ifAbsent) {
        return delegate.addToMetadata(metadata, user, ifAbsent);
    }

    @Override
    public UserProvider andThen(UserProvider other) {
        return delegate.andThen(other);
    }
}

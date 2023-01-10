/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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
public abstract class AbstractUserProvider implements UserProvider {

    private final String metadataKey;
    private final Class<? extends User> userClass;

    public AbstractUserProvider(Class<? extends User> userClass) {
        this("$user", userClass);
    }

    @Override
    public User fromMessage(HasMessage message) {
        return message.getMetadata().get(metadataKey, userClass);
    }

    @Override
    public boolean containsUser(Metadata metadata) {
        return metadata.containsKey(metadataKey);
    }

    @Override
    public Metadata removeFromMetadata(Metadata metadata) {
        return metadata.without(metadataKey);
    }

    @Override
    public Metadata addToMetadata(Metadata metadata, User user) {
        return metadata.with(metadataKey, user);
    }

}

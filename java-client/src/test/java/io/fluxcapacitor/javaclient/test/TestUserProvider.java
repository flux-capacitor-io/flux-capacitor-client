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

package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

import java.util.Optional;

@AllArgsConstructor
public class TestUserProvider implements UserProvider {
    @Delegate(excludes = ExcludedMethods.class)
    private final UserProvider delegate;

    @Override
    public User getActiveUser() {
        var result = delegate.getActiveUser();
        if (result == null && Optional.ofNullable(DeserializingMessage.getCurrent())
                .map(m -> m.getMessageType() != MessageType.WEBREQUEST).orElse(true)) {
            return getSystemUser();
        }
        return result;
    }

    private interface ExcludedMethods {
        User getActiveUser();
    }
}

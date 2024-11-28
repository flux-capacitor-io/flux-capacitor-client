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

package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
public class DefaultEventGateway implements EventGateway {
    @Delegate
    private final GenericGateway delegate;

    @Override
    public CompletableFuture<Void> publish(Message message, Guarantee guarantee) {
        return delegate.sendAndForget(message, guarantee);
    }

    @Override
    public void publish(Object... messages) {
        publish(Guarantee.NONE, messages);
    }

    @Override
    public CompletableFuture<Void> publish(Guarantee guarantee, Object... messages) {
        return sendAndForget(guarantee, messages);
    }
}

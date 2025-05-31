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
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;


/**
 * Default implementation of the {@link MetricsGateway} interface.
 * <p>
 * This class delegates all functionality to an underlying {@link GenericGateway} instance, enabling the use of metrics
 * gateway methods while leveraging the generic gateway's capabilities.
 *
 * @see MetricsGateway
 * @see GenericGateway
 */
@AllArgsConstructor
@Slf4j
public class DefaultMetricsGateway implements MetricsGateway {
    @Delegate
    private final GenericGateway delegate;

    @Override
    public CompletableFuture<Void> publish(Object payload, Metadata metadata, Guarantee guarantee) {
        return delegate.sendAndForget(new Message(payload, metadata), guarantee);
    }
}

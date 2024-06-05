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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.handling.MessageFilter;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.HasMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.routing.RoutingKey;
import io.fluxcapacitor.javaclient.tracking.Tracker;

import java.lang.reflect.Executable;
import java.util.Optional;
import java.util.function.Function;

import static io.fluxcapacitor.javaclient.common.ClientUtils.memoize;

public class SegmentFilter implements MessageFilter<HasMessage> {
    private final Function<Executable, Optional<RoutingKey>> routingKeyCache =
            memoize(e -> ReflectionUtils.getMethodAnnotation(e, RoutingKey.class));

    @Override
    public boolean test(HasMessage message, Executable executable) {
        return message instanceof DeserializingMessage dm
               && Tracker.current().filter(tracker -> tracker.getConfiguration().ignoreSegment())
                       .map(tracker -> routingKeyCache.apply(executable)
                               .map(routingKey -> message.getRoutingKey(routingKey.value())
                                       .or(message::computeRoutingKey)
                                       .orElseGet(message::getMessageId))
                               .map(routingValue -> tracker.canHandle(dm, routingValue))
                               .orElse(true)).orElse(true);
    }
}

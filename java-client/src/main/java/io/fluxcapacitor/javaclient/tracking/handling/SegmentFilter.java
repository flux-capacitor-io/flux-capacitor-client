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
import io.fluxcapacitor.javaclient.tracking.Consumer;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.Tracker;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.Optional;
import java.util.function.Function;

import static io.fluxcapacitor.javaclient.common.ClientUtils.memoize;

/**
 * A {@link MessageFilter} that restricts handler invocation based on segment membership, using routing keys.
 *
 * <p>This filter applies **only** when client-side segment filtering is enabled via the {@code ignoreSegment = true}
 * setting in the {@link ConsumerConfiguration} or {@link Consumer}. In such scenarios, handlers annotated with
 * {@link RoutingKey} will be evaluated to ensure that only messages matching the local segment range are processed by
 * the client.
 *
 * <p>When active, the filter performs the following logic:
 * <ul>
 *   <li>Extracts the routing key for the message using the {@link RoutingKey} annotation on the handler method.
 *       If the annotation is missing or the routing key cannot be resolved, the message ID is used as a fallback.</li>
 *   <li>Delegates to {@link Tracker#canHandle(DeserializingMessage, String)} to check whether the message falls
 *       within the current client's segment range.</li>
 *   <li>If the message is not an instance of {@link DeserializingMessage}, the filter always returns {@code true}.</li>
 * </ul>
 *
 * <p>This ensures that trackers that use client-side message filtering do not redundantly handle messages
 * that don't fall within the tracker's segment range, ensuring that each message is handled only once in a
 * distributed system.
 *
 * @see RoutingKey
 * @see Tracker
 * @see Consumer#ignoreSegment()
 * @see HasMessage#getRoutingKey(String)
 */
public class SegmentFilter implements MessageFilter<HasMessage> {
    private final Function<Executable, Optional<RoutingKey>> routingKeyCache =
            memoize(e -> ReflectionUtils.getMethodAnnotation(e, RoutingKey.class));

    @Override
    public boolean test(HasMessage message, Executable executable, Class<? extends Annotation> handlerAnnotation) {
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

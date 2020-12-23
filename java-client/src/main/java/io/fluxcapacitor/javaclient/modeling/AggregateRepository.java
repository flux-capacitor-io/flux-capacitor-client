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

package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourced;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.MessageType.NOTIFICATION;
import static io.fluxcapacitor.javaclient.modeling.AggregateIdResolver.getAggregateId;
import static io.fluxcapacitor.javaclient.modeling.AggregateTypeResolver.getAggregateType;

public interface AggregateRepository {

    boolean supports(Class<?> aggregateType);

    boolean cachingAllowed(Class<?> aggregateType);

    /**
     * Loads the aggregate root of type {@code <T>} with given id.
     * <p>
     * If the aggregate is loaded while handling an event of the aggregate, the returned Aggregate will automatically be
     * replayed back to event currently being handled. Otherwise, the most recent state of the aggregate is loaded.
     *
     * @see EventSourced for more info on how to define an event sourced aggregate root
     */
    default <T> Aggregate<T> load(String aggregateId, Class<T> aggregateType) {
        Aggregate<T> result = load(aggregateId, aggregateType, false);
        DeserializingMessage message = DeserializingMessage.getCurrent();
        if (message != null && (message.getMessageType() == EVENT || message.getMessageType() == NOTIFICATION)
                && aggregateId.equals(getAggregateId(message)) && aggregateType.equals(getAggregateType(message))) {
            return result.playBackToEvent(message.getSerializedObject().getMessageId());
        }
        return result;
    }

    <T> Aggregate<T> load(String aggregateId, Class<T> aggregateType, boolean onlyCached);

}

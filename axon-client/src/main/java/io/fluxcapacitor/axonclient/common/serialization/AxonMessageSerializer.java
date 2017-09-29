/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

package io.fluxcapacitor.axonclient.common.serialization;

import io.fluxcapacitor.javaclient.eventsourcing.Snapshot;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.messaging.Message;

import java.util.stream.Stream;

public interface AxonMessageSerializer {

    byte[] serialize(Message<?> message);

    byte[] serializeEvent(EventMessage<?> message);

    byte[] serializeDomainEvent(DomainEventMessage<?> message);

    byte[] serializeCommand(CommandMessage<?> message);

    Message<?> deserializeMessage(io.fluxcapacitor.common.api.Message message);

    CommandMessage<?> deserializeCommand(io.fluxcapacitor.common.api.Message message);

    Stream<? extends EventMessage<?>> deserializeEvents(Stream<io.fluxcapacitor.common.api.Message> messageStream);

    DomainEventStream deserializeDomainEvents(Stream<io.fluxcapacitor.common.api.Message> messageStream);

    DomainEventMessage<?> deserializeSnapshot(Snapshot snapshot);

}

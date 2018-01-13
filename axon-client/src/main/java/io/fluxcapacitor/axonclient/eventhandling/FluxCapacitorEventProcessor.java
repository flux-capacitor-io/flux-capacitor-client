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

package io.fluxcapacitor.axonclient.eventhandling;

import io.fluxcapacitor.axonclient.common.serialization.AxonMessageSerializer;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Message;
import io.fluxcapacitor.javaclient.tracking.Tracking;
import io.fluxcapacitor.javaclient.tracking.TrackingService;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.eventhandling.*;
import org.axonframework.messaging.unitofwork.BatchingUnitOfWork;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

@Slf4j
public class FluxCapacitorEventProcessor extends AbstractEventProcessor {

    private final TrackingService trackingService;
    private final AxonMessageSerializer serializer;
    private final int threads;
    private volatile Registration registration;

    public FluxCapacitorEventProcessor(String name, List<?> eventHandlers,
                                       TrackingService trackingService, AxonMessageSerializer serializer) {
        this(name, new SimpleEventHandlerInvoker(eventHandlers), RollbackConfigurationType.ANY_THROWABLE,
             PropagatingErrorHandler.INSTANCE, NoOpMessageMonitor.INSTANCE, trackingService, serializer, 1);
    }

    public FluxCapacitorEventProcessor(String name, EventHandlerInvoker eventHandlerInvoker,
                                       RollbackConfiguration rollbackConfiguration, ErrorHandler errorHandler,
                                       MessageMonitor<? super EventMessage<?>> messageMonitor,
                                       TrackingService trackingService, AxonMessageSerializer serializer, int threads) {
        super(name, eventHandlerInvoker, rollbackConfiguration, errorHandler, messageMonitor);
        this.trackingService = trackingService;
        this.serializer = serializer;
        this.threads = threads;
    }

    protected void handle(List<Message> messages) {
        List<EventMessage<?>> events = serializer.deserializeEvents(messages.stream()).collect(toList());
        try {
            log.info("{} received events {}", getName(),
                     events.stream().map(org.axonframework.messaging.Message::getPayloadType).map(Class::getSimpleName)
                             .collect(toList()));
            UnitOfWork<? extends EventMessage<?>> unitOfWork = new BatchingUnitOfWork<>(events);
            super.processInUnitOfWork(events, unitOfWork, Segment.ROOT_SEGMENT);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new EventProcessingException("Exception occurred while processing events", e);
        }
    }

    @Override
    public void start() {
        if (registration == null) {
            registration = Tracking.start(getName(), threads, trackingService, this::handle);
        }
    }

    @Override
    public void shutDown() {
        Optional.ofNullable(registration).ifPresent(Registration::cancel);
        registration = null;
    }
}

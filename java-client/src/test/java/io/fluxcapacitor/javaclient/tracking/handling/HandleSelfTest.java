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

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationException;
import io.fluxcapacitor.javaclient.tracking.metrics.HandleMessageEvent;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

class HandleSelfTest {

    final TestFixture testFixture = TestFixture.createAsync();

    @Test
    void query() {
        testFixture.whenQuery(new Object() {
            @HandleQuery
            String handleSelf() {
                return "foo";
            }
        }).expectResult("foo").expectNoMetrics();
    }

    @Test
    void command() {
        Object payload = new Object() {
            @HandleCommand
            void handleSelf() {
                FluxCapacitor.publishEvent(this);
            }
        };
        testFixture.whenCommand(payload).expectNoResult().expectEvents(payload);
    }

    @Test
    void disabled() {
        testFixture.registerHandlers(new Object() {
            @HandleCommand
            void handleCommand(Object command) {
                FluxCapacitor.publishEvent(true);
            }
        }).whenCommand(new DisabledHandleSelf()).expectOnlyEvents(true).expectNoErrors();
    }

    @Test
    void handleSelfIgnoredIfEvent() {
        Object payload = new Object() {
            @HandleEvent
            void handleSelf() {
                FluxCapacitor.publishEvent("foo");
            }
        };
        testFixture.whenEvent(payload).expectNoErrors().expectNoEvents();
    }

    @Test
    void logTrackingMetrics() {
        testFixture.whenQuery(new Object() {
            @HandleQuery
            @LocalHandler(logMetrics = true)
            String handleSelf() {
                return "foo";
            }
        }).expectMetrics(HandleMessageEvent.class);
    }

    @Test
    void logMessage() {
        testFixture.registerHandlers(new Object() {
            @HandleCommand
            void handleCommand(Object command) {
                FluxCapacitor.publishEvent(true);
            }
        }).whenCommand(new MessageLoggingHandleSelf()).expectEvents("foo", true);
    }

    @Test
    void doNotLogMessage() {
        testFixture.registerHandlers(new Object() {
            @HandleCommand
            void handleCommand(Object command) {
                FluxCapacitor.publishEvent(true);
            }
        }).whenCommand(new EventPublishingHandleSelf()).expectEvents("foo").expectNoEventsLike(true);
    }

    @Test
    void triggersException() {
        testFixture.whenQuery(new Object() {
            @HandleQuery
            String handleSelf() {
                throw new MockException();
            }
        }).expectExceptionalResult(MockException.class);
    }

    @Test
    void triggersValidationException() {
        testFixture.whenQuery(new Object() {
            @NotBlank
            private final String foo = null;

            @HandleQuery
            String handleSelf() {
                return "bar";
            }
        }).expectExceptionalResult(ValidationException.class);
    }

    static class EventPublishingHandleSelf {
        @HandleCommand
        void handle() {
            FluxCapacitor.publishEvent("foo");
        }
    }

    static class MessageLoggingHandleSelf {
        @HandleCommand
        @LocalHandler(logMessage = true)
        void handle() {
            FluxCapacitor.publishEvent("foo");
        }
    }

    static class DisabledHandleSelf {
        @HandleCommand(disabled = true)
        void handle() {
            FluxCapacitor.publishEvent("foo");
        }
    }
}
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
import io.fluxcapacitor.javaclient.tracking.Consumer;
import io.fluxcapacitor.javaclient.tracking.TrackSelf;
import io.fluxcapacitor.javaclient.tracking.Tracker;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationException;
import io.fluxcapacitor.javaclient.tracking.metrics.HandleMessageEvent;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HandleSelfTest {

    final TestFixture testFixture = TestFixture.create();

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
        class DisabledHandleSelf {
            @HandleCommand(disabled = true)
            void handle() {
                FluxCapacitor.publishEvent("foo");
            }
        }

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

    @Test
    void syncTrackSelfHandledWithoutRegistration() {
        @TrackSelf
        @AllArgsConstructor
        class SelfTracked {
            String input;

            @HandleQuery
            String handleSelf() {
                return "bar";
            }
        }

        testFixture.whenQuery(new SelfTracked("foo")).expectResult("bar");
    }

    @Nested
    class AsyncTests {

        final TestFixture testFixture = TestFixture.createAsync();

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
    }

    @Nested
    class TrackSelfTests {

        final TestFixture testFixture = TestFixture.createAsync();

        @TrackSelf
        @Consumer(name = "SelfTracked")
        @Value
        static class SelfTracked {
            String input;

            @HandleQuery
            String handleSelf() {
                if (Tracker.current().isEmpty()) {
                    return "no tracker";
                }
                if (Tracker.current().isPresent()
                    && "SelfTracked".equals(Tracker.current().get().getConfiguration().getName())) {
                    return input;
                }
                return "wrong consumer";
            }
        }

        @TrackSelf
        @Consumer(name = "SelfTracked")
        interface SelfTrackedInterface {
            String getInput();

            @HandleQuery
            default String handleSelf() {
                if (Tracker.current().isEmpty()) {
                    return "no tracker";
                }
                if (Tracker.current().isPresent()
                    && "SelfTracked".equals(Tracker.current().get().getConfiguration().getName())) {
                    return getInput();
                }
                return "wrong consumer";
            }
        }

        @Value
        static class SelfTrackedConcrete implements SelfTrackedInterface {
            String input;
        }

        @Test
        void queryTracked() {
            testFixture.registerHandlers(SelfTracked.class)
                    .whenQuery(new SelfTracked("foo")).expectResult("foo");
        }

        @Test
        void queryTrackedInterface() {
            testFixture.registerHandlers(SelfTrackedInterface.class)
                    .whenQuery(new SelfTrackedConcrete("foo")).expectResult("foo");
        }
    }
}
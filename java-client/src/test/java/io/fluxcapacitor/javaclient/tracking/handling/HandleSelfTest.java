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
            @HandleSelf
            String handleSelf() {
                return "foo";
            }
        }).expectResult("foo").expectNoMetrics();
    }

    @Test
    void command() {
        Object payload = new Object() {
            @HandleSelf
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
            @HandleSelf
            void handleSelf() {
                FluxCapacitor.publishEvent("foo");
            }
        };
        testFixture.whenEvent(payload).expectNoErrors().expectNoEvents();
    }

    @Test
    void logTrackingMetrics() {
        testFixture.whenQuery(new Object() {
            @HandleSelf(logMetrics = true)
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
            @HandleSelf
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

            @HandleSelf
            String handleSelf() {
                return "bar";
            }
        }).expectExceptionalResult(ValidationException.class);
    }

    static class EventPublishingHandleSelf {
        @HandleSelf
        void handle() {
            FluxCapacitor.publishEvent("foo");
        }
    }

    static class MessageLoggingHandleSelf {
        @HandleSelf(logMessage = true)
        void handle() {
            FluxCapacitor.publishEvent("foo");
        }
    }

    static class DisabledHandleSelf {
        @HandleSelf(disabled = true)
        void handle() {
            FluxCapacitor.publishEvent("foo");
        }
    }
}
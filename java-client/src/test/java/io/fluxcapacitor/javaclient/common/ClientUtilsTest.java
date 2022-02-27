package io.fluxcapacitor.javaclient.common;

import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.LocalHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientUtilsTest {

    @Test
    void handleOnlyTracking() throws Exception {
        assertTrue(ClientUtils.isTrackingHandler(Handler.class, Handler.class.getDeclaredMethod("handle", String.class)));
        assertFalse(ClientUtils.isLocalHandler(Handler.class, Handler.class.getDeclaredMethod("handle", String.class)));
    }

    @Test
    void handleOnlyLocal() throws Exception {
        assertTrue(ClientUtils.isLocalHandler(Handler.class, Handler.class.getDeclaredMethod("handleOnlyLocal", int.class)));
        assertFalse(ClientUtils.isTrackingHandler(Handler.class, Handler.class.getDeclaredMethod("handleOnlyLocal", int.class)));
    }

    @Test
    void handleLocalAndExternal() throws Exception {
        assertTrue(ClientUtils.isLocalHandler(Handler.class, Handler.class.getDeclaredMethod("handleLocalOrExternal", double.class)));
        assertTrue(ClientUtils.isTrackingHandler(Handler.class, Handler.class.getDeclaredMethod("handleLocalOrExternal", double.class)));
    }

    private static class Handler {
        @HandleCommand
        String handle(String command) {
            return command;
        }

        @HandleCommand
        @LocalHandler
        int handleOnlyLocal(int command) {
            return command;
        }

        @HandleCommand
        @LocalHandler(allowExternalMessages = true)
        double handleLocalOrExternal(double command) {
            return command;
        }
    }
}
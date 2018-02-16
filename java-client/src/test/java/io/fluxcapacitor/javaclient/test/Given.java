package io.fluxcapacitor.javaclient.test;

public interface Given {

    When givenCommands(Object... commands);

    When givenEvents(Object... events);

    default When givenNoPriorActivity() {
        return givenCommands();
    }

}

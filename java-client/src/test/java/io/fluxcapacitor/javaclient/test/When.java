package io.fluxcapacitor.javaclient.test;

public interface When {
    When andGivenCommands(Object... commands);

    When andGivenEvents(Object... events);

    Then whenCommand(Object command);

    Then whenEvent(Object event);

    Then whenQuery(Object query);
    
    Then when(Runnable task);
}

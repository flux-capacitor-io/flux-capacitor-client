package io.fluxcapacitor.javaclient.test;

public interface When {
    When andGivenCommands(Object... commands);

    Then whenCommand(Object command);
}

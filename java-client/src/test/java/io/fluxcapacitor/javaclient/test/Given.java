package io.fluxcapacitor.javaclient.test;

public interface Given {

    When givenCommands(Object... commands);

    default When givenNoPriorActivity() {
        return givenCommands();
    }

}

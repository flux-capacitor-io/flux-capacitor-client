package io.fluxcapacitor.javaclient.common;

import lombok.NonNull;

import java.util.concurrent.Executor;

public enum DirectExecutor implements Executor {
    INSTANCE;

    @Override
    public void execute(@NonNull Runnable command) {
        command.run();
    }
}

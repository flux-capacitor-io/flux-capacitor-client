package io.fluxcapacitor.common;

@FunctionalInterface
public interface ThrowingRunnable {
    void run() throws Exception;
}

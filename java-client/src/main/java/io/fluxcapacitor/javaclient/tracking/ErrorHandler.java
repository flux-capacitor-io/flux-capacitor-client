package io.fluxcapacitor.javaclient.tracking;

@FunctionalInterface
public interface ErrorHandler {
    void handleError(Exception error, String errorMessage, Runnable retryFunction) throws Exception;
}

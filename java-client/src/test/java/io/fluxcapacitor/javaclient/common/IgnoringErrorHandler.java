package io.fluxcapacitor.javaclient.common;

import io.fluxcapacitor.javaclient.tracking.ErrorHandler;

public class IgnoringErrorHandler implements ErrorHandler {
    @Override
    public void handleError(Exception error, String errorMessage, Runnable retryFunction) throws Exception {
        //no op
    }
}

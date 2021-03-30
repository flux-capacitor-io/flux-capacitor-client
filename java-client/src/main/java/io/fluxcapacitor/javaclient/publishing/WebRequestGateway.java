package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;
import io.fluxcapacitor.javaclient.web.WebRequest;
import io.fluxcapacitor.javaclient.web.WebResponse;

import java.util.concurrent.CompletableFuture;


public interface WebRequestGateway extends HasLocalHandlers {
    void sendAndForget(WebRequest webRequest);

    CompletableFuture<WebResponse> send(WebRequest webRequest);

    WebResponse sendAndWait(WebRequest webRequest);

}

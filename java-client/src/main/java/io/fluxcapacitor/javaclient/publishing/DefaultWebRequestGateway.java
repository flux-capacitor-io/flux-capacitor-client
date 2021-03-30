package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.javaclient.web.WebRequest;
import io.fluxcapacitor.javaclient.web.WebResponse;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
@Slf4j
public class DefaultWebRequestGateway implements WebRequestGateway {
    @Delegate
    private final GenericGateway delegate;

    @Override
    public void sendAndForget(WebRequest webRequest) {
        delegate.sendAndForget(webRequest);
    }

    @Override
    public CompletableFuture<WebResponse> send(WebRequest webRequest) {
        return delegate.sendForMessage(webRequest);
    }

    @Override
    public WebResponse sendAndWait(WebRequest webRequest) {
        return delegate.sendAndWaitForMessage(webRequest);
    }
}

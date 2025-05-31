/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.javaclient.web.WebRequest;
import io.fluxcapacitor.javaclient.web.WebRequestSettings;
import io.fluxcapacitor.javaclient.web.WebResponse;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Default implementation of the {@link WebRequestGateway} interface that delegates requests to a configured
 * {@link GenericGateway}. This class acts as a bridge for handling outbound web requests using Flux Platform’s proxy
 * mechanism.
 * <p>
 * It supports sending web requests in both asynchronous (fire-and-forget, future-based) and synchronous (blocking)
 * manners, utilizing the underlying delegate to process the actual interactions with the Flux platform.
 *
 * @see WebRequestGateway
 * @see GenericGateway
 */
@AllArgsConstructor
public class DefaultWebRequestGateway implements WebRequestGateway {
    @Delegate
    private final GenericGateway delegate;

    @Override
    public CompletableFuture<Void> sendAndForget(Guarantee guarantee, WebRequest... requests) {
        return delegate.sendAndForget(guarantee, requests);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public CompletableFuture<WebResponse> send(WebRequest request) {
        return (CompletableFuture) delegate.sendForMessage(request);
    }

    @Override
    @SneakyThrows
    public WebResponse sendAndWait(WebRequest request, WebRequestSettings settings) {
        try {
            request = request.addMetadata("settings", settings);
            return (WebResponse) delegate.sendForMessage(request).get(settings.getTimeout().toMillis() + 1000L, MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new TimeoutException(format("Request %s (url %s) has timed out", request.getMessageId(),
                                              WebRequest.getUrl(request.getMetadata())));
        } catch (InterruptedException e) {
            currentThread().interrupt();
            throw new GatewayException(
                    format("Thread interrupted while waiting for result of %s (url %s)",
                           request.getMessageId(), WebRequest.getUrl(request.getMetadata())), e);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }
}

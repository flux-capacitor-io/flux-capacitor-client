/*
 * Copyright (c) 2016-2018 Flux Capacitor. 
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

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.SneakyThrows;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;
import static java.lang.Thread.currentThread;

public interface RequestGateway {

    default <R> CompletableFuture<R> send(Message message) {
        return send(message.getPayload(), message.getMetadata());
    }

    default <R> CompletableFuture<R> send(Object payload) {
        if (payload instanceof Message) {
            return send(((Message) payload).getPayload(), ((Message) payload).getMetadata());
        } else {
            return send(payload, Metadata.empty());
        }
    }

    default <R> CompletableFuture<R> send(Object payload, Metadata metadata) {
        return sendForMessage(payload, metadata).thenApply(Message::getPayload);
    }

    CompletableFuture<Message> sendForMessage(Object payload, Metadata metadata);

    default <R> R sendAndWait(Message message) {
        return sendAndWait(message.getPayload(), message.getMetadata());
    }
    
    default <R> R sendAndWait(Object payload) {
        if (payload instanceof Message) {
            return sendAndWait(((Message) payload).getPayload(), ((Message) payload).getMetadata());
        } else {
            return sendAndWait(payload, Metadata.empty());
        }
    }

    @SneakyThrows
    default <R> R sendAndWait(Object payload, Metadata metadata) {
        CompletableFuture<R> future = send(payload, metadata);
        try {
            Timeout timeout = payload.getClass().getAnnotation(Timeout.class);
            if (timeout != null) {
                return future.get(timeout.millis(), TimeUnit.MILLISECONDS);
            }
            return future.get();
        } catch (java.util.concurrent.TimeoutException e) {
            throw new TimeoutException(
                    format("%s has timed out", payload), e);
        } catch (InterruptedException e) {
            currentThread().interrupt();
            throw new GatewayException(format("Thread interrupted while waiting for result of %s", payload), e);
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }
    
}

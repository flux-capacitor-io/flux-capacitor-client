/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;
import io.fluxcapacitor.javaclient.tracking.handling.Request;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface CommandGateway extends HasLocalHandlers {

    void sendAndForget(Object command);

    void sendAndForget(Object payload, Metadata metadata);

    void sendAndForget(Object payload, Metadata metadata, Guarantee guarantee);

    void sendAndForget(Object... messages);

    CompletableFuture<Void> sendAndForget(Guarantee guarantee, Object... messages);

    <R> CompletableFuture<R> send(Object command);

    <R> CompletableFuture<R> send(Object payload, Metadata metadata);

    CompletableFuture<Message> sendForMessage(Message message);

    <R> List<CompletableFuture<R>> send(Object... messages);

    List<CompletableFuture<Message>> sendForMessages(Message... messages);

    <R> R sendAndWait(Object command);

    <R> R sendAndWait(Object payload, Metadata metadata);

    <R> CompletableFuture<R> send(Request<R> query);

    <R> CompletableFuture<R> send(Request<R> payload, Metadata metadata);

    <R> R sendAndWait(Request<R> query);

    <R> R sendAndWait(Request<R> payload, Metadata metadata);

    void close();
}

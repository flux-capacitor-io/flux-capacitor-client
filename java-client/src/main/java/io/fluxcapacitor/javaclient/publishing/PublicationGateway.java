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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;

public interface PublicationGateway {

    default void sendAndForget(Object payload) {
        sendAndForget(payload instanceof Message ? (Message) payload : new Message(payload, getMessageType()));
    }

    default void sendAndForget(Object payload, Metadata metadata) {
        sendAndForget(new Message(payload, metadata, getMessageType()));
    }
    
    void sendAndForget(Message message);

    Registration registerLocalHandler(Object handler);

    MessageType getMessageType();
    
}

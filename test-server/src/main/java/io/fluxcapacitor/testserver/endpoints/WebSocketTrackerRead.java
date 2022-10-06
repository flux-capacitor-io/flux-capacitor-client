/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.testserver.endpoints;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.Read;
import io.fluxcapacitor.javaclient.tracking.client.TrackerRead;
import lombok.Value;
import lombok.experimental.Delegate;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Value
public class WebSocketTrackerRead implements TrackerRead {
    @Delegate
    Read read;
    String clientId;
    String sessionId;
    long deadline;
    Predicate<String> typeFilter;
    MessageType messageType;

    public WebSocketTrackerRead(Read read, String clientId, String sessionId, MessageType messageType) {
        this.read = read;

        this.typeFilter = toPredicate(read.getTypeFilter());
        this.deadline = System.currentTimeMillis() + read.getMaxTimeout();

        this.clientId = clientId;
        this.sessionId = sessionId;
        this.messageType = messageType;
    }

    private static Predicate<String> toPredicate(String typeFilter) {
        if (typeFilter == null) {
            return s -> true;
        }
        return Pattern.compile(typeFilter).asMatchPredicate();
    }

    public boolean canHandle(SerializedMessage message) {
        return (isIgnoreMessageTarget() || message.getTarget() == null || clientId.equals(message.getTarget()))
                && (message.getData().getType() == null || typeFilter.test(message.getData().getType()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WebSocketTrackerRead that = (WebSocketTrackerRead) o;
        return Objects.equals(getConsumer(), that.getConsumer()) &&
               Objects.equals(getTrackerId(), that.getTrackerId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getConsumer(), getTrackerId());
    }

    @Override
    public String toString() {
        return "WebSocketTracker{" +
               "consumerName='" + getConsumer() + '\'' +
               ", clientId='" + clientId + '\'' +
               ", trackerId='" + getTrackerId() + '\'' +
               '}';
    }
}

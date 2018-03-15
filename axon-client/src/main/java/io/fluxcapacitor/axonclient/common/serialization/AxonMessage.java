/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

package io.fluxcapacitor.axonclient.common.serialization;

import lombok.Value;

@Value
public class AxonMessage {
    String id;
    byte[] payload;
    byte[] metadata;
    String type;
    String revision;

    Long timestamp;
    String domain;
    String aggregateId;
    Long sequenceNumber;

    String commandName;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private byte[] payload;
        private byte[] metadata;
        private String type;
        private String revision;
        private Long timestamp;
        private String domain;
        private String aggregateId;
        private Long sequenceNumber;
        private String commandName;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder payload(byte[] payload) {
            this.payload = payload;
            return this;
        }

        public Builder metadata(byte[] metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder revision(String revision) {
            this.revision = revision;
            return this;
        }

        public Builder timestamp(Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder aggregateId(String aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        public Builder sequenceNumber(Long sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }

        public Builder commandName(String commandName) {
            this.commandName = commandName;
            return this;
        }

        public AxonMessage build() {
            return new AxonMessage(id, payload, metadata, type, revision, timestamp, domain, aggregateId, sequenceNumber,
                                   commandName);
        }
    }
}

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

package io.fluxcapacitor.common.api;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Wither;

import java.time.Clock;

@lombok.Data
@AllArgsConstructor
public class SerializedMessage implements SerializedObject<byte[], SerializedMessage> {

    private static final ThreadLocal<Clock> clock = ThreadLocal.withInitial(Clock::systemUTC);

    @Wither
    @NonNull
    private Data<byte[]> data;
    private Metadata metadata;
    @Wither
    private Integer segment;
    private Long index;
    private String source;
    private String target;
    private Integer requestId;
    private Long timestamp;
    private String messageId;

    public SerializedMessage(Data<byte[]> data, Metadata metadata, String messageId) {
        this.data = data;
        this.metadata = metadata;
        this.timestamp = clock.get().millis();
        this.messageId = messageId;
    }

    @Override
    public Data<byte[]> data() {
        return data;
    }

    public static void useCustomClock(Clock customClock) {
        clock.set(customClock);
    }

    public static void useDefaultClock() {
        clock.set(Clock.systemUTC());
    }

}

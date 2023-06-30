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

package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.InMemoryEventStoreClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.TestUtils.assertEqualMessages;
import static io.fluxcapacitor.common.TestUtils.createMessages;

class InMemoryEventStoreClientTest {

    private final InMemoryEventStoreClient subject = new InMemoryEventStoreClient();

    @Test
    void returnsCorrectStreamWhenLastSnIsMidBatch() throws Exception {
        List<SerializedMessage> in = createMessages(300);
        subject.storeEvents("a", in, false);
        Stream<SerializedMessage> out = subject.getEvents("a", 99L);
        assertEqualMessages(in.subList(100, 300), out.collect(Collectors.toList()));
    }
}
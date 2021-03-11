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

package io.fluxcapacitor.javaclient.tracking.handling.validation;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import lombok.Value;
import org.junit.jupiter.api.Test;

import javax.validation.constraints.NotNull;
import java.time.Clock;
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidatingInterceptorTest {

    private ValidatingInterceptor subject = new ValidatingInterceptor();
    private Function<Object, DeserializingMessage> messageFactory = payload -> new DeserializingMessage(
            new DeserializingObject<>(new SerializedMessage(new Data<>(
                    "test".getBytes(), "test", 0, null), Metadata.empty(), "someId",
                                                            Clock.systemUTC().millis()), () -> payload), EVENT);

    @Test
    void testWithConstraintViolations() {
        DeserializingMessage message = messageFactory.apply(new ConstrainedObject(null));
        assertThrows(ValidationException.class,
                () -> subject.interceptHandling(m -> null, null, "test").apply(message));
    }

    @Test
    void testWithoutConstraintViolations() {
        DeserializingMessage message = messageFactory.apply(new ConstrainedObject("foo"));
        subject.interceptHandling(m -> null, null, "test").apply(message);
    }

    @Value
    private static class ConstrainedObject {
        @NotNull String aString;
    }

}
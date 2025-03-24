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

package io.fluxcapacitor.javaclient.test;

import com.fasterxml.jackson.databind.ObjectWriter;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.SneakyThrows;
import lombok.Value;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.platform.commons.util.ExceptionUtils;
import org.opentest4j.AssertionFailedError;

import java.util.Collection;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

public class GivenWhenThenAssertionError extends AssertionFailedError {
    public static ObjectWriter formatter = JsonUtils.reader.writerWithDefaultPrettyPrinter();

    public GivenWhenThenAssertionError(String message) {
        super(message);
    }

    public GivenWhenThenAssertionError(String message, Throwable cause) {
        super(message, cause);
    }

    public GivenWhenThenAssertionError(String message, Object expected, Object actual) {
        super(message, formatForComparison(expected), formatForComparison(actual));
    }

    public GivenWhenThenAssertionError(String message, Object expected, Object actual, Throwable cause) {
        super(message, formatForComparison(expected), formatForComparison(actual), cause);
    }

    @SneakyThrows
    private static Object formatForComparison(Object expectedOrActual) {
        if (expectedOrActual instanceof Message message) {
            Metadata metadata = message.getMetadata().withoutIf(key -> key.startsWith("$"));
            return new PayloadAndMetadata(message.getPayload(), metadata);
        }
        if (expectedOrActual instanceof Collection<?> collection) {
            return collection.stream().map(GivenWhenThenAssertionError::formatForComparison).collect(toList());
        }
        if (expectedOrActual instanceof Throwable) {
            return ExceptionUtils.readStackTrace((Throwable) expectedOrActual);
        }
        if (ResultValidator.matchersSupported) {
            if (expectedOrActual instanceof Matcher<?>) {
                return expectedOrActual;
            }
            if (expectedOrActual instanceof Description) {
                return Objects.toString(expectedOrActual);
            }
        }
        try {
            return expectedOrActual instanceof CharSequence
                    ? expectedOrActual
                    : formatter.writeValueAsString(expectedOrActual).replaceAll("\\\\n", "\n");
        } catch (Exception e) {
            return expectedOrActual;
        }
    }

    @Value
    private static class PayloadAndMetadata {
        Object payload;
        Metadata metadata;

        @Override
        public String toString() {
            try {
                return formatter.writeValueAsString(this).replaceAll("\\\\n", "\n");
            } catch (Exception e) {
                return "Message{" +
                        "payload=" + payload +
                        ", metadata=" + metadata +
                        ", payloadType=" + getPayloadType() +
                        '}';
            }
        }

        public String getPayloadType() {
            return payload == null ? Void.class.getName() : payload.getClass().getName();
        }
    }


}

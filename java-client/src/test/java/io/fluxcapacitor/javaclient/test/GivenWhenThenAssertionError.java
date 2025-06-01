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

/**
 * Specialized {@link AssertionFailedError} used by the Flux Capacitor testing framework to signal assertion failures
 * during {@code then} phase validations in a {@code given-when-then} test.
 * <p>
 * This exception provides enhanced formatting for improved diffing between expected and actual values. Specifically,
 * if the compared values include:
 * <ul>
 *   <li>{@link Message} instances – only user metadata is retained (technical metadata is stripped), and payload is shown separately</li>
 *   <li>{@link Throwable} instances – stack traces are extracted and rendered as strings</li>
 *   <li>Other objects – serialized using a pretty-printed JSON formatter</li>
 * </ul>
 * <p>
 * Formatting is handled internally via {@link #formatForComparison(Object)}. Differences are then passed to the parent
 * {@link AssertionFailedError} constructor to enable clear diffs in IDEs and test runners.
 */
public class GivenWhenThenAssertionError extends AssertionFailedError {

    /**
     * Shared JSON object writer used to serialize expected and actual values for comparison.
     * Uses {@link JsonUtils} with default pretty printer enabled.
     */
    public static ObjectWriter formatter = JsonUtils.reader.writerWithDefaultPrettyPrinter();

    /**
     * Constructs an assertion error with only a message.
     *
     * @param message the failure message
     */
    public GivenWhenThenAssertionError(String message) {
        super(message);
    }

    /**
     * Constructs an assertion error with a message and a cause.
     *
     * @param message the failure message
     * @param cause   the exception that caused this failure
     */
    public GivenWhenThenAssertionError(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs an assertion error with a message and expected/actual values to compare.
     * <p>
     * The values are preprocessed and serialized to human-readable form using {@link #formatForComparison(Object)}.
     *
     * @param message  the failure message
     * @param expected the expected value
     * @param actual   the actual value
     */
    public GivenWhenThenAssertionError(String message, Object expected, Object actual) {
        super(message, formatForComparison(expected), formatForComparison(actual));
    }

    /**
     * Constructs an assertion error with a message, expected/actual values, and a cause.
     * <p>
     * The values are preprocessed and serialized to human-readable form using {@link #formatForComparison(Object)}.
     *
     * @param message  the failure message
     * @param expected the expected value
     * @param actual   the actual value
     * @param cause    the exception that caused this failure
     */
    public GivenWhenThenAssertionError(String message, Object expected, Object actual, Throwable cause) {
        super(message, formatForComparison(expected), formatForComparison(actual), cause);
    }

    /**
     * Prepares an object for textual comparison by transforming it into a clean and readable form:
     * <ul>
     *   <li>For {@link Message} instances: wraps into a {@link PayloadAndMetadata} without technical metadata</li>
     *   <li>For {@link Collection} instances: formats each element individually</li>
     *   <li>For {@link Throwable}: extracts and returns stack trace</li>
     *   <li>For other values: serializes to pretty-printed JSON if possible</li>
     * </ul>
     *
     * @param expectedOrActual the object to format
     * @return a readable representation of the value for use in assertion output
     */
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

    /**
     * Helper class used to render a message's payload and stripped-down metadata cleanly in assertion output.
     */
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

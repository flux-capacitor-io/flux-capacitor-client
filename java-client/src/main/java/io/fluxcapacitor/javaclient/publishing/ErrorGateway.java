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

package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;
import lombok.SneakyThrows;

import java.util.concurrent.CompletableFuture;

/**
 * Gateway interface for reporting errors during message handling.
 * <p>
 * The {@code ErrorGateway} is responsible for publishing error messages that occur during the processing of
 * commands, queries, schedules, web requests, or other types of messages. These error messages are typically
 * forwarded to a centralized error stream, which may be consumed for diagnostics, alerting, auditing, or automated
 * handling.
 *
 * <p>This interface provides methods to report errors as plain objects or as {@link Message} instances, along with
 * optional {@link Metadata} and a {@link Guarantee} that controls the dispatching behavior (e.g. SENT vs STORED).
 *
 * <h2>Automatic Error Reporting</h2>
 * Flux Capacitor automatically reports exceptions that are thrown during handler method invocation. This includes
 * both {@link RuntimeException}s and checked exceptions wrapped in runtime exceptions. In most cases, users do not
 * need to manually report errors using this gateway.
 *
 * <p>Flux can also integrate with popular logging frameworks (e.g. Logback, Log4j, SLF4J) to monitor and report any
 * logged warnings or errors as {@code ERROR} messages, depending on configuration. This provides deep visibility
 * into both application logic failures and operational anomalies.
 *
 * <h2>Manual Use Cases</h2>
 * Manual usage of this gateway is only necessary in edge cases such as:
 * <ul>
 *   <li>Forwarding functional errors from external systems that are not raised as exceptions.</li>
 *   <li>Publishing structured error messages for use in dashboards, alerting systems, or external consumers.</li>
 *   <li>Custom pipelines where message handling does not involve regular handler mechanisms.</li>
 * </ul>
 *
 * @see HasLocalHandlers
 * @see Guarantee
 * @see io.fluxcapacitor.javaclient.common.exception.FunctionalException
 * @see io.fluxcapacitor.javaclient.common.exception.TechnicalException
 * @see ResultGateway for sending command/query results instead of errors
 */
public interface ErrorGateway extends HasLocalHandlers {

    /**
     * Reports an error using the provided object. If the object is a {@link Message}, it is passed through with its
     * payload and metadata intact. Otherwise, it is wrapped in a new message with empty metadata.
     *
     * @param error the error object or {@link Message} to report
     */
    default void report(Object error) {
        if (error instanceof Message) {
            report(((Message) error).getPayload(), ((Message) error).getMetadata());
        } else {
            report(error, Metadata.empty());
        }
    }

    /**
     * Reports an error with the given payload and metadata using the default {@link Guarantee#NONE}.
     * <p>
     * This method blocks until the reporting operation is completed or fails.
     *
     * @param payload  the error payload
     * @param metadata context metadata for the error
     */
    @SneakyThrows
    default void report(Object payload, Metadata metadata) {
        report(payload, metadata, Guarantee.NONE).get();
    }

    /**
     * Reports an error with the given payload, metadata, and delivery guarantee.
     * <p>
     * Returns a future that completes once the error message is sent or stored based on the provided guarantee.
     *
     * @param payload   the error payload
     * @param metadata  metadata describing the context of the error
     * @param guarantee whether to ensure message is sent ({@link Guarantee#SENT}) or stored ({@link Guarantee#STORED})
     * @return a future that completes once the message has been handled appropriately
     */
    CompletableFuture<Void> report(Object payload, Metadata metadata, Guarantee guarantee);
}

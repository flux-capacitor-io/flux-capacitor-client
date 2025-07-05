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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * Declares a {@code Consumer} within a Flux application.
 * <p>
 * A consumer represents an isolated group of handlers that independently track and process messages from one or more
 * message logs. It can be applied at the class or package level to group handlers together. Handlers that do not
 * explicitly declare a {@code Consumer} are assigned according to the application's configuration, as defined via
 * {@link FluxCapacitorBuilder#addConsumerConfiguration(ConsumerConfiguration, MessageType...)} )}. If no specific
 * configuration is provided, the handler will be assigned to the application's default consumer.
 * </p>
 *
 * <p>
 * A consumer consists of one or more <em>trackers</em>—individual threads or processes that fetch and process message
 * segments. Each tracker is responsible for a disjoint segment of the message log, allowing for parallel consumption.
 * By default, messages are sharded into 128 segments; a consumer with {@code threads = 2} will assign 64 segments to
 * each tracker.
 * </p>
 *
 * <p>
 * This annotation offers fine-grained control over message processing characteristics including concurrency, batching,
 * backpressure, result publication, and handler exclusivity.
 * </p>
 *
 * <h2>Terminology</h2>
 * <ul>
 *   <li><strong>Consumer</strong>: Named group of handlers with isolated message tracking state.</li>
 *   <li><strong>Tracker</strong>: A processing thread assigned to a specific segment of the message log.</li>
 *   <li><strong>Handler</strong>: Method annotated with {@code @HandleEvent}, {@code @HandleCommand}, etc., which processes messages.</li>
 * </ul>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * @Consumer(name = "audit", threads = 3, passive = true)
 * class AuditHandler {
 *     @HandleCommand
 *     void on(AuthenticateUser command) {
 *         // log for auditing; result will not be published due to passive = true
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Inherited
@Documented
public @interface Consumer {

    /**
     * The unique name of the consumer. Required. This isolates its tracking tokens from other consumers.
     */
    String name();

    /**
     * The number of tracker threads to allocate for this consumer. Each thread processes a unique segment of the
     * message log. Default is {@code 1}.
     */
    int threads() default 1;

    /**
     * Maximum number of messages to fetch in a batch. Default is {@code 1024}.
     */
    int maxFetchSize() default 1024;

    /**
     * Maximum time to wait before fetching a new batch, when none are available. See {@link #durationUnit()} for the
     * time unit. Default is {@code 60} (seconds).
     */
    long maxWaitDuration() default 60;

    /**
     * Unit for {@link #maxWaitDuration()}. Default is {@link ChronoUnit#SECONDS}.
     */
    ChronoUnit durationUnit() default SECONDS;

    /**
     * Interceptors applied to individual handler method invocations.
     */
    Class<? extends HandlerInterceptor>[] handlerInterceptors() default {};

    /**
     * Interceptors applied at the batch level across all messages in a poll cycle.
     */
    Class<? extends BatchInterceptor>[] batchInterceptors() default {};

    /**
     * Error handler invoked when a message processing error occurs. Default is {@link LoggingErrorHandler} which logs
     * errors and allows message tracking and processing to continue.
     */
    Class<? extends ErrorHandler> errorHandler() default LoggingErrorHandler.class;

    /**
     * Regulates message flow and backpressure behavior. Default is {@link NoOpFlowRegulator}.
     */
    Class<? extends FlowRegulator> flowRegulator() default NoOpFlowRegulator.class;

    /**
     * If {@code true}, only messages explicitly targeted at this application instance will be processed. Typically used
     * for tracking of {@code Result} or {@code WebResponse} messages. If {@code true}, this consumer will only receive
     * results targeted for this application instance.
     */
    boolean filterMessageTarget() default false;

    /**
     * If {@code true}, this consumer will bypass the default segment-based sharding applied by the Flux platform and
     * attempt to process all message segments.
     * <p>
     * By default, Flux shards messages across consumers using a routing key present in the message payload, or the
     * message ID if no routing key is specified. However, some handlers may require a custom sharding strategy— for
     * instance, sharding based on a different property in the payload.
     * </p>
     *
     * <p>
     * Setting {@code ignoreSegment = true} allows such handlers to override Flux's internal routing and apply their own
     * logic. A common pattern is to use the {@code @RoutingKey} annotation on a handler method to specify a custom
     * property:
     * </p>
     *
     * <pre>{@code
     * @HandleEvent
     * @RoutingKey("organisationId")
     * void handle(CreateUser event) {
     *     // process based on organisationId instead of the default routing key
     * }
     * }</pre>
     */
    boolean ignoreSegment() default false;

    /**
     * If {@code true}, designates a single tracker within this consumer as the "main" tracker, responsible for
     * processing all messages across all segments.
     * <p>
     * Although multiple tracker threads may be configured (via {@link #threads()}), only one tracker will be assigned
     * all segments. Other trackers will remain idle and receive no segment assignments.
     * </p>
     *
     * <p>
     * This setting is useful when:
     * <ul>
     *   <li>Messages must be processed strictly in global index order by a single process.</li>
     *   <li>No suitable routing key exists for meaningful partitioning.</li>
     *   <li>Handler logic requires a holistic or stateful view of all messages across the log.</li>
     * </ul>
     *
     * <p>
     * In contrast to regular segmented consumers, this mode disables concurrent processing
     * across trackers but ensures strict ordering.
     * </p>
     */
    boolean singleTracker() default false;

    /**
     * If {@code true}, the consumer will not rely on Flux's internal tracking index. Instead, the application itself is
     * responsible for determining which messages to process.
     * <p>
     * This is typically used in combination with {@link #ignoreSegment()} set to {@code true} to ensure that all
     * application instances receive every message—rather than a sharded subset.
     * </p>
     *
     * <p>
     * This mode is useful for scenarios where message delivery must be broadcast to all instances. For example, a
     * WebSocket endpoint that pushes updates to connected clients may need to observe the full message stream, ensuring
     * that each client sees every relevant update.
     * </p>
     *
     * <p>
     * When {@code false} (the default), Flux tracks message indices and distributes segments to consumer trackers for
     * balanced parallel processing.
     * </p>
     */
    boolean clientControlledIndex() default false;

    /**
     * Whether this consumer is taking manual control over storing its position in the log.
     * <p>
     * When {@code true}, the consumer is responsible for explicitly storing its position after processing one or more
     * message batches. This allows for greater control — for example, when handling long-running workflows that span
     * multiple batches, or when committing position should be deferred until post-processing is complete.
     * <p>
     * When {@code false} (the default), the position is automatically updated after each message batch is processed,
     * ensuring progress is recorded and avoiding reprocessing on restart.
     * <p>
     * Note: Even with manual position tracking enabled, the consumer will continue to receive "new" messages as long as
     * the tracking process remains active. However, its persisted position will not be updated unless explicitly stored.
     */
    boolean storePositionManually() default false;

    /**
     * Determines whether handlers assigned to this consumer are excluded from other consumers.
     * <p>
     * If {@code true} (default), a handler will only be active in this consumer.
     * If {@code false}, the same handler may be active in multiple consumers simultaneously.
     * This enables advanced scenarios such as parallel replays alongside live processing.
     * </p>
     */
    boolean exclusive() default true;

    /**
     * Indicates that this consumer should process messages without publishing result messages.
     * <p>
     * When {@code true}, return values from request handlers (e.g., {@code @HandleCommand}, {@code @HandleQuery},
     * {@code @HandleWebRequest}) are ignored and not appended to the result log. This is useful for secondary consumers
     * that perform side-effects or projections without impacting the result flow.
     */
    boolean passive() default false;

    /**
     * Optional minimum message index from which this consumer should begin processing.
     * <p>
     * If set to a non-negative value, only messages at or above this index will be processed. If negative (the
     * default), the consumer will start processing from the current end of the message log – i.e., it will only receive
     * new messages from this point forward.
     * </p>
     */
    long minIndex() default -1L;

    /**
     * Optional exclusive upper bound for message processing. Messages at or above this index will not be processed.
     * Ignored if negative.
     */
    long maxIndexExclusive() default -1L;

    /**
     * Optional regular expression used to filter message payload types on the Flux platform.
     * <p>
     * When specified, this filter is applied server-side to restrict the messages delivered to the consumer based on
     * the fully qualified type name of the payload.
     * </p>
     *
     * <p>
     * If left empty (the default), all message types are delivered to the client, and filtering is performed locally by
     * the handlers. This is typically the preferred approach, as it avoids tightly coupling consumer configuration to
     * type naming and allows for greater flexibility.
     * </p>
     *
     * <p>
     * Example: {@code typeFilter = ".*\\.CreateUser$|.*\\.UpdateUser$"} matches any {@code CreateUser} or
     * {@code UpdateUser} message types, regardless of package. This is useful for selectively tracking a set of message
     * types without tying the filter to specific namespaces.
     * </p>
     */
    String typeFilter() default "";
}

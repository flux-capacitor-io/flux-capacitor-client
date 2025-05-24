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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.javaclient.modeling.EntityId;
import io.fluxcapacitor.javaclient.modeling.HandlerRepository;
import io.fluxcapacitor.javaclient.persisting.search.Searchable;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a class is a stateful message handler — i.e., one whose state is persisted and which can receive
 * messages via {@link Association}.
 * <p>
 * Stateful handlers are used to model long-lived processes or external interactions (e.g. async API flows, payments,
 * inboxes). These handlers are typically stored and restored across messages and retain their internal state.
 *
 * <h2>Handler Activation</h2>
 * When a message matches one or more handlers (via {@link Association}), all matching handlers are:
 * <ul>
 *     <li>Loaded from storage (usually the search index)</li>
 *     <li>And invoked via matching {@code @Handle...} methods.</li>
 * </ul>
 * <p>
 * A single message may invoke <strong>multiple matching stateful handlers</strong>.
 *
 * <h2>Persistence</h2>
 * Handler state is persisted via a {@link HandlerRepository}.
 * By default, the {@link io.fluxcapacitor.javaclient.persisting.search.DocumentStore} is used.
 * <ul>
 *     <li>The identifier of a handler is derived from an {@link EntityId} property or auto-generated if absent.</li>
 *     <li>Handlers are immutable by convention and updated using {@code withX(...)} methods.</li>
 * </ul>
 *
 * <h3>Basic Example</h3>
 * <pre>{@code
 * @Value
 * @Stateful
 * public class PaymentProcess {
 *     @EntityId String id;
 *     @Association String pspReference;
 *     PaymentStatus status;
 *
 *     @HandleEvent
 *     static PaymentProcess on(PaymentInitiated event) {
 *         String pspReference = FluxCapacitor.sendCommandAndWait(new ExecutePayment(...));
 *         return new PaymentProcess(event.getPaymentId(), pspReference, PaymentStatus.PENDING);
 *     }
 *
 *     @HandleEvent
 *     PaymentProcess on(PaymentConfirmed event) {
 *         return withStatus(PaymentStatus.CONFIRMED);
 *     }
 * }
 * }</pre>
 *
 * <h3>Deleting a stateful handler</h3>
 * If a handler method returns {@code null}, the instance is removed from its persistence store.
 * This can be useful for modeling short-lived processes or sagas that end upon receiving a terminal event.
 *
 * <pre>{@code
 * @HandleEvent
 * PaymentProcess on(PaymentFailed event) {
 *     return null; // Delete this handler
 * }
 * }</pre>
 *
 * <h3>Type constraints on handler updates</h3>
 * State changes to a {@code @Stateful} handler are only applied if the method returns a value that is
 * assignable to the handler's class type. For example:
 *
 * <ul>
 *     <li>If the method returns {@code void}, no update occurs.</li>
 *     <li>If the method returns a value that is unrelated to the handler type, the return value is ignored for state updates.</li>
 * </ul>
 *
 * <p>
 * This makes it safe to return primitive types or utility values from handler methods without risk of
 * overwriting the handler state. For instance:
 *
 * <pre>{@code
 * @HandleSchedule
 * Duration on(CheckPaymentStatus schedule) {
 *     PaymentStatus status = FluxCapacitor.queryAndWait(new CheckStatus(schedule.getPaymentId()));
 *     if (status == COMPLETED) {
 *         return null; // stop scheduling
 *     }
 *     return Duration.ofMinutes(1); // retry in 1 minute
 * }
 * }</pre>
 *
 * <p>
 * In this example, returning a {@code Duration} controls the rescheduling behavior, but it does not affect the stored
 * state of the {@code PaymentProcess} handler.
 *
 * <h2>Search Indexing</h2>
 * Like aggregates, {@code @Stateful} handlers are also {@link Searchable} and can be indexed automatically:
 * <ul>
 *     <li>{@link #collection()} defines the search collection name</li>
 *     <li>{@link #timestampPath()} and {@link #endPath()} define time bounds for filtering</li>
 * </ul>
 *
 * <h2> Tracking Isolation</h2>
 * {@code @Stateful} handlers may optionally include a {@link io.fluxcapacitor.javaclient.tracking.Consumer} annotation
 * to define their own tracking configuration (e.g. isolation, concurrency, retry policy).
 *
 * @see Association
 * @see EntityId
 * @see io.fluxcapacitor.javaclient.tracking.Consumer
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Searchable
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public @interface Stateful {

    /**
     * Name of the collection in which the stateful handler instance will be stored.
     * <p>
     * Defaults to the simple class name (e.g., {@code PaymentProcess} → {@code paymentProcess}).
     *
     * @see Searchable
     */
    String collection() default "";

    /**
     * Path to extract the main timestamp used in search indexing.
     * <p>
     * If {@link #endPath()} is not specified, this will be used as both start and end time.
     * <p>
     * Useful for time-based search queries (e.g., validity or activity windows).
     *
     * @see Searchable
     */
    String timestampPath() default "";

    /**
     * Optional path to extract an end timestamp for search indexing.
     * <p>
     * If omitted, the start timestamp will also be used as the end timestamp.
     *
     * @see Searchable
     */
    String endPath() default "";

    /**
     * Determines whether the state changes to this handler should be committed at the end of the current message batch
     * (if applicable), or immediately after the message that triggered the change (default behavior).
     * <p>
     * If set to {@code true}, changes are deferred and committed once all messages in the current batch have been
     * processed. This is particularly useful for reducing round-trips to the underlying persistence store when applying
     * lots of updates.
     *
     * <h4>Association behavior with deferred commits</h4>
     * Even though the state is not yet persisted when {@code commitInBatch} is {@code true}, the message routing logic
     * remains accurate and consistent. This is achieved by maintaining a local cache of uncommitted changes.
     * <p>
     * The cache is used to:
     * <ul>
     *     <li>Ensure that newly created handlers can be matched to later messages in the same batch.</li>
     *     <li>Prevent messages from being routed to handlers that have been deleted earlier in the batch.</li>
     *     <li>Use the most recent (updated) state when evaluating associations for subsequent messages.</li>
     * </ul>
     * <p>
     * In other words, association lookups during batch processing always take into account:
     * <ul>
     *     <li>The persisted state (from the backing repository), and</li>
     *     <li>The in-memory cache of batch-local updates (created, updated, or deleted handlers).</li>
     * </ul>
     * <p>
     * This guarantees consistent and predictable behavior within the boundaries of the current batch, even when the
     * persistent state has not yet been flushed.
     *
     * <h4>Note:</h4>
     * If no current batch is active (e.g. in synchronous or local usage), changes are always committed immediately,
     * regardless of this setting.
     */
    boolean commitInBatch() default false;
}

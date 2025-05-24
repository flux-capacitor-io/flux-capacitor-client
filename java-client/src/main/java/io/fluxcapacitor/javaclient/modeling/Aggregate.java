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

package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.persisting.search.Searchable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as the root of an aggregate in the domain model.
 * <p>
 * Aggregates are the primary consistency boundaries in Flux. They may consist of a root entity (the annotated class)
 * and any number of nested child entities, which are registered using the {@link Member @Member} annotation.
 * <p>
 * This annotation also allows fine-grained configuration of event sourcing, caching, snapshotting, and automatic
 * indexing in Flux’s document store.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Aggregate
 * public class Project {
 *     @EntityId String projectId;
 *
 *     @Member List<Task> tasks;
 * }
 * }</pre>
 *
 * <h2>Loading and Modifying Aggregates</h2>
 * Aggregates can be loaded using methods like {@link io.fluxcapacitor.javaclient.FluxCapacitor#loadAggregate}. Updates
 * are applied via {@link Apply @Apply} methods, and legality checks can be enforced using
 * {@link AssertLegal @AssertLegal}.
 *
 * <h2>Child Entities</h2>
 * Use {@link Member} to define child, grandchild, or other descendant entities in the aggregate. These entities can
 * independently handle updates and be targeted via their {@link EntityId}.
 *
 * @see Member
 * @see Apply
 * @see AssertLegal
 * @see Searchable
 * @see io.fluxcapacitor.javaclient.FluxCapacitor#loadAggregate
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Searchable
public @interface Aggregate {

    /**
     * Whether the aggregate should use event sourcing (enabled by default).
     * <p>
     * When enabled, applied updates are stored in the event store and used to reconstruct the aggregate state.
     * <p>
     * Disabling event sourcing may be useful for read-heavy aggregates or reference data that seldom changes.
     * {@link Apply} methods still work even when event sourcing is disabled, but updates will not be stored.
     */
    boolean eventSourced() default true;

    /**
     * Whether to ignore unknown events during event sourcing (disabled by default).
     * <p>
     * If disabled and an unknown event is encountered, an error will occur when the aggregate is loaded.
     */
    boolean ignoreUnknownEvents() default false;

    /**
     * Enables snapshotting after a certain number of applied updates.
     * <p>
     * Default is {@code 0} (disabled). Use a positive value (e.g., {@code 1000}) to trigger snapshotting every
     * {@code n} events.
     * <p>
     * Useful for aggregates with many events or large event payloads.
     */
    int snapshotPeriod() default 0;

    /**
     * Whether the aggregate should be cached after updates (enabled by default).
     * <p>
     * Aggregates are always cached thread-locally before being committed. This setting controls whether the latest
     * version is stored in the shared application cache.
     */
    boolean cached() default true;

    /**
     * Controls how many versions of the aggregate should be retained in the cache.
     * <p>
     * A value of {@code -1} (default) means all versions are cached.
     * <p>
     * Use {@code 0} to only cache the latest version. Values {@code > 0} retain older versions, enabling use of
     * {@link io.fluxcapacitor.javaclient.modeling.Entity#previous()}.
     */
    int cachingDepth() default -1;

    /**
     * Sets the event checkpointing frequency for intermediate aggregate states.
     * <p>
     * Only applies to event-sourced aggregates with a positive {@link #cachingDepth()}.
     */
    int checkpointPeriod() default 100;

    /**
     * Whether changes to the aggregate should be committed at the end of a message batch (enabled by default).
     * <p>
     * Set to {@code false} to commit updates immediately after each message.
     */
    boolean commitInBatch() default true;

    /**
     * Controls whether updates that don’t change the aggregate result in events.
     * <p>
     * Use {@link EventPublication#IF_MODIFIED} to automatically skip events if nothing changes.
     * <p>
     * This setting is evaluated before {@link #publicationStrategy()}.
     */
    EventPublication eventPublication() default EventPublication.DEFAULT;

    /**
     * Strategy that determines how applied updates are persisted and/or published.
     * <p>
     * The default strategy is {@link EventPublicationStrategy#STORE_AND_PUBLISH}, unless overridden.
     */
    EventPublicationStrategy publicationStrategy() default EventPublicationStrategy.DEFAULT;

    /**
     * Whether the aggregate should be indexed in Flux’s document store (disabled by default).
     */
    boolean searchable() default false;

    /**
     * Name of the collection to index the aggregate into. Only relevant if {@link #searchable()} is {@code true}.
     * <p>
     * Defaults to the class name if left blank.
     *
     * @see Searchable
     */
    String collection() default "";

    /**
     * Path to extract the main timestamp used in search indexing. Only relevant if {@link #searchable()} is
     * {@code true}.
     * <p>
     * If {@link #endPath()} is not specified, this will be used as both start and end time.
     * <p>
     * Useful for time-based search queries (e.g., validity or activity windows).
     *
     * @see Searchable
     */
    String timestampPath() default "";

    /**
     * Optional path to extract an end timestamp for search indexing. Only relevant if {@link #searchable()} is
     * {@code true}.
     * <p>
     * If omitted, the start timestamp will also be used as the end timestamp.
     *
     * @see Searchable
     */
    String endPath() default "";
}

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

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.InterceptApply;
import io.fluxcapacitor.javaclient.persisting.search.Searchable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be placed on classes that are root entities in your domain. Use methods of this annotation to configure
 * where to store the aggregate and load it from.
 * <p>
 * To load an aggregate or make changes to an aggregate, first load the aggregate using e.g.
 * {@link FluxCapacitor#loadAggregate}. If the {@link Aggregate} annotation is missing from the aggregate class that is
 * loaded, Flux Capacitor will use the defaults of {@link Aggregate}.
 * <p>
 * An aggregate consist of this root entity and may contain any number of child entities. To add child entities to the
 * aggregate, just annotate any field in the aggregate class with {@link Member @Member}.
 *
 * @see Member for more information on how to add child, or grand-child etc., entities to an aggregate.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Searchable
public @interface Aggregate {
    /**
     * Determines whether this aggregate should be event-sourced when it is loaded. True by default.
     * <p>
     * Event-sourcing means that the state of the aggregate is stored as an append-only event log containing every
     * change made to the aggregate. When the aggregate is loaded, all its changes are fetched from the event log and
     * replayed to rebuild the aggregate. This has the advantage that you can keep your aggregate model flexible and
     * make retroactive changes to your model at later times.
     * <p>
     * In many cases it is suitable to use event-sourcing for your domain models, but there are also situations where
     * event-sourcing is not advisable, i.e. if you expect to apply many updates (think >100,000) to individual
     * aggregates or when you need to store millions of aggregate instances as reference data that typically don't
     * change much. However, if you're not sure yet, it is typically a good idea to keep event-sourcing enabled until
     * you are.
     * <p>
     * If event-sourcing is enabled, every object that is applied to this aggregate will, upon commit of the aggregate,
     * be stored in the event store and published to the global event log, unless explicitly disabled using
     * {@link Apply @Apply}.
     * <p>
     * Note that updates can still be applied to an aggregate using {@link Apply @Apply} methods even if event-sourcing
     * is disabled.
     *
     * @see Apply on how to apply updates to an aggregate or its sub-entities.
     */
    boolean eventSourced() default true;

    /**
     * Determines whether events of unknown type will be ignored during event-sourcing. If this is false (the default)
     * and any event's type is unknown, an exception is raised when the aggregate is loaded.
     */
    boolean ignoreUnknownEvents() default false;

    /**
     * Determines whether to create snapshots of an event-sourced aggregate. If the snapshotPeriod is 0 (the default),
     * snapshotting is disabled. If it is positive, the number reflects the number of events that needs to be applied to
     * the aggregate before a new snapshot of the aggregate is stored.
     * <p>
     * Snapshotting the aggregate may be useful when you expect the aggregate to receive many updates (events), i.e.
     * typically far more than 10,000 updates, or when the updates are very large. For typical use cases that require
     * snapshotting, set the snapshotPeriod at 1000 or so.
     * <p>
     * When snapshotting is enabled, Flux Capacitor first attempts to load the latest snapshot of the aggregate before
     * any later events are fetched from the event store and applied to the aggregate. If a snapshot cannot be fetched
     * or deserialized, the aggregate will be event-sourced instead, and the corrupt snapshot will automatically be
     * deleted.
     * <p>
     * Beware of snapshotting because it will make your domain model less flexible, as your model will now be persisted
     * alongside its events. However, do know it is possible to define upcasters for your snapshots just as easily as
     * for events or other message payloads.
     */
    int snapshotPeriod() default 0;

    /**
     * Determines whether the aggregate should be cached after it has been updated and committed.
     * <p>
     * For any event-sourced aggregate this is typically a good idea. However, it is advisable to disable this when the
     * aggregate is not event-sourced and the aggregate will be loaded in more than one application instance.
     * <p>
     * Note that changes to an aggregate before a commit will always be cached thread-locally even when cached() is
     * false. That means you can apply multiple updates to an aggregate in the same handler method.
     */
    boolean cached() default true;

    /**
     * Setting to control how many versions of the aggregate will be stored in the cache. I.e. how many times
     * {@link Entity#previous()} can be invoked on the aggregate before event sourcing is necessary.
     * <p>
     * Note that loading an aggregate inside an event handler will automatically play back the aggregate to the moment
     * of the event. I.e. even if your code does not invoke {@link Entity#previous()} anywhere, this setting will still
     * be relevant.
     * <p>
     * A negative depth (the default) implies that every version of the aggregate will be cached. For event sourced
     * aggregates that can become quite large (events in the 100s or more) it is therefore advisable to specify a depth.
     * Note that a depth of 0 only caches the most recent version of the aggregate.
     * <p>
     * This setting does nothing if {@link #cached()} or {@link #eventSourced()} is {@code false}.
     */
    int cachingDepth() default -1;

    /**
     * Setting to control after which number of applied events the state of an aggregate should be stored in the cache
     * to serve as checkpoint for event sourcing when requesting later version of the aggregate.
     * <p>
     * Note that this setting only applies for older versions of the aggregate, i.e. for sequence numbers smaller than
     * or equal to the most recent sequence number minus the {@link #cachingDepth()}.
     * <p>
     * This period should be greater than or equal to 1. For a period of 1 all versions of the aggregate will be cached,
     * which can also be achieved by selecting a negative {@link #cachingDepth()} (the default).
     * <p>
     * This setting does nothing if {@link #cached()} or {@link #eventSourced()} is {@code false}.
     */
    int checkpointPeriod() default 100;

    /**
     * Determines whether changes to this aggregate should be committed at end of the current message batch of the
     * current tracker or the end of the current message.
     * <p>
     * If a change is made outside the context of a message batch or current message, any change will always be
     * committed immediately.
     */
    boolean commitInBatch() default true;

    /**
     * Setting to control event publication.
     * <p>
     * Use {@code @Aggregate(eventPublication = IF_MODIFIED)} to stop events from being published if the aggregate does
     * not change, removing the need for dedicated {@link InterceptApply} methods for this.
     * <p>
     * Use {@code @Aggregate(eventPublication = NEVER)} to prevent event publication altogether. This is useful because
     * it allows command application on aggregates with {@code eventSourcing = false} without giving rise to events.
     */
    EventPublication eventPublication() default EventPublication.DEFAULT;

    /**
     * Setting that determines what happens to published events. Note that {@link #eventPublication()} is checked first
     * to determine if an applied event should be published at all. Only then is checked how the events are to be
     * published given the strategy.
     */
    EventPublicationStrategy publicationStrategy() default EventPublicationStrategy.DEFAULT;

    /**
     * Determines whether changes to this aggregate should automatically be committed to the document store. Use other
     * methods of this annotation to configure which search collection to store the aggregate in, etc.
     */
    boolean searchable() default false;

    /**
     * Returns the name of the collection in which the handler should be stored. Defaults to the simple name of Handler
     * class.
     *
     * @see Searchable
     */
    String collection() default "";

    /**
     * Returns the name of property on the handler that contains a timestamp associated with the handler. This may be
     * useful in case the handlers need to e.g. be presented in an overview.
     *
     * @see Searchable
     */
    String timestampPath() default "";

    /**
     * Returns the name of property on the handler that contains an end timestamp associated with the handler. This may
     * be useful in case the handlers need to e.g. be presented in an overview.
     *
     * @see Searchable
     */
    String endPath() default "";
}

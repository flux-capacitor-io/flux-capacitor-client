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

import io.fluxcapacitor.javaclient.persisting.eventsourcing.InterceptApply;
import io.fluxcapacitor.javaclient.persisting.search.Searchable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Searchable
public @interface Aggregate {
    boolean eventSourced() default true;

    boolean ignoreUnknownEvents() default false;

    int snapshotPeriod() default 0;

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

    boolean searchable() default false;

    String collection() default "";

    String timestampPath() default "";

    String endPath() default "";
}

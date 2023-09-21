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
    EventPublication eventPublication() default EventPublication.ALWAYS;

    /**
     * Setting that determines what happens to published events. Note that {@link #eventPublication()} is checked first
     * to determine if an applied event should be published at all. Only then is checked how the events are to be
     * published given the strategy.
     */
    EventPublicationStrategy publicationStrategy() default EventPublicationStrategy.STORE_AND_PUBLISH;

    boolean searchable() default false;

    String collection() default "";

    String timestampPath() default "";

}

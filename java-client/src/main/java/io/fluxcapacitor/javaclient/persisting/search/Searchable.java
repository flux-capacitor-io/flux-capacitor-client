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

package io.fluxcapacitor.javaclient.persisting.search;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that instances of the annotated type should be indexed for search within Flux Capacitor.
 * <p>
 * This annotation provides metadata to guide how an object is serialized and stored in a search index, including
 * which collection it belongs to and how timestamps are derived. Annotating a class with {@code @Searchable} enables
 * it to be stored and queried using {@link io.fluxcapacitor.javaclient.persisting.search.DocumentStore}.
 * <p>
 * This annotation can also be used as a meta-annotation to define custom searchable types with preconfigured values.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Searchable(collection = "projects", timestampPath = "createdAt")
 * public class Project {
 *     @EntityId String id;
 *     Instant createdAt;
 *     String name;
 * }
 * }</pre>
 *
 * <h3>Searchable value classes can be published using:</h3>
 * <ul>
 *     <li>{@link io.fluxcapacitor.javaclient.FluxCapacitor#index(Object)}</li>
 *     <li>{@link io.fluxcapacitor.javaclient.persisting.search.DocumentStore}</li>
 * </ul>
 *
 * @see io.fluxcapacitor.javaclient.persisting.search.DocumentStore
 * @see io.fluxcapacitor.javaclient.persisting.search.IndexOperation
 */
@Documented
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Searchable {

    /**
     * Whether the annotated type should be included in search indexing.
     * <p>
     * Set to {@code false} to explicitly opt out of indexing even if other settings are present.
     */
    boolean searchable() default true;

    /**
     * The name of the collection where instances of this class should be indexed.
     * <p>
     * If left blank, the collection name will default to the simple class name (e.g., {@code Project}).
     */
    String collection() default "";

    /**
     * A path expression used to extract the primary timestamp from the object for indexing.
     * <p>
     * This timestamp is used for sorting, querying ranges, and time-based document retrieval.
     * The path can point to a property (e.g., {@code "createdAt"}).
     * <p>
     * If an {@code endPath} is not provided, the document will be stored with both the start and end
     * equal to the timestamp extracted via this path. This is useful to represent point-in-time events
     * (e.g., a complaint submission) versus open-ended reference data (e.g., a record that is active
     * from a certain moment onward).
     */
    String timestampPath() default "";

    /**
     * A path expression used to extract an optional end timestamp from the object.
     * <p>
     * If set, this value is stored alongside the main timestamp to define a time window. This can be useful
     * to model data that is valid between two moments in time. If left empty, the start timestamp is also used as the end.
     */
    String endPath() default "";
}

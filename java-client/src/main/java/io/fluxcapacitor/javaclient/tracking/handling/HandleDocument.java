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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.serialization.Revision;
import io.fluxcapacitor.javaclient.persisting.search.Searchable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or constructor as a handler for document messages within a search collection.
 * <p>
 * This is a specialization of {@link HandleMessage} for {@link MessageType#DOCUMENT} messages. It allows consuming
 * updates from the document store in near real-time—similar to event tracking. Handlers can either specify a collection
 * name or a document class.
 * </p>
 *
 * <h2>Document Tracking Semantics</h2>
 * <p>
 * Each time a document is (re)indexed in Flux Capacitor, it receives a new tracking index. When a handler is subscribed
 * via {@code @HandleDocument}, it will receive the most recent version of the document for each index. If the tracker
 * is behind (e.g., during a replay), earlier versions of the same document may be skipped—only the latest version is
 * retained.
 * </p>
 *
 * <h2>Transforming and Updating Documents</h2>
 * <p>
 * A powerful feature of document handlers is that they can return a modified version of the document to update it
 * in-place. This allows document transformations and upcasting to be applied automatically and reliably.
 * </p>
 * <p>
 * For this behavior to take effect:
 * <ul>
 *   <li>The returned document must have a higher {@link Revision} than the one stored</li>
 *   <li>The updated version will be stored in the document collection under the same ID</li>
 * </ul>
 *
 * <p>
 * This mechanism supports fully-automated data migrations: handlers can evolve or patch documents over time,
 * and changes are persisted across application restarts.
 * </p>
 *
 * @see Searchable
 * @see HandleMessage
 * @see MessageType#DOCUMENT
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@HandleMessage(MessageType.DOCUMENT)
public @interface HandleDocument {
    /**
     * Optional name of the document collection. If provided, {@link #documentClass()} is ignored.
     *
     * <p>
     * If neither documentClass nor value are specified, the first parameter of the method is used to determine the
     * collection:
     *
     * <pre>{@code
     * class OrganisationHandler {
     *     @HandleDocument
     *     void on(Organisation organisation, Metadata metadata) { ... }
     * }
     * }</pre>
     *
     * @see Searchable
     */
    String value() default "";

    /**
     * Optional class of the documents to handle. If annotated with {@link Searchable}, the annotation defines the
     * collection; otherwise the class name is used.
     * <p>
     * If neither documentClass nor value are specified, the first parameter of the method is used to determine the
     * collection:
     *
     * <pre>{@code
     * class OrganisationHandler {
     *     @HandleDocument
     *     void on(Organisation organisation, Metadata metadata) { ... }
     * }
     * }</pre>
     *
     * @see Searchable
     */
    Class<?> documentClass() default Void.class;

    /**
     * If {@code true}, disables this handler during discovery.
     */
    boolean disabled() default false;
}

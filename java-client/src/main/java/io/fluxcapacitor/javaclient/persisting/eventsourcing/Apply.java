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

package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.javaclient.modeling.EntityId;
import io.fluxcapacitor.javaclient.modeling.EventPublication;
import io.fluxcapacitor.javaclient.modeling.EventPublicationStrategy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a method or constructor applies an update to an entity, or creates or deletes an entity.
 * <p>
 * An {@code @Apply} method defines how a specific update modifies an entity. This update is typically the payload of a
 * command or other message expressing intent. Once validated and applied, the update may be published and/or stored as
 * an event, depending on the publication configuration.
 * <p>
 * {@code @Apply} can be placed:
 * <ul>
 *     <li>On a method inside the <strong>update class</strong> (e.g. {@code UpdateProduct#apply(Product)}),
 *         which receives the current state of the entity and returns the updated version</li>
 *     <li>On a method or static factory in the <strong>entity class</strong> itself
 *         (e.g. {@code Product#update(UpdateProduct)}), which describes how the entity processes a given update</li>
 *     <li>On a <strong>constructor</strong> or static method of the entity, if the update creates a new instance</li>
 * </ul>
 * <p>
 * For deletions, returning {@code null} signals that the entity should be removed.
 * <p>
 * When the entity is part of a larger aggregate, Flux automatically routes the update to the correct entity
 * instance using matching identifier fields, typically annotated with {@link EntityId}.
 * <p>
 * {@code @Apply} methods are also used during event sourcing to reconstruct an entity's state from past updates.
 * <p>
 * Method parameters are injected automatically. Supported parameters include:
 * <ul>
 *     <li>The current entity instance (for non-static apply methods)</li>
 *     <li>Any parent, grandparent, or other ancestor entity in the aggregate hierarchy</li>
 *     <li>The update object itself</li>
 *     <li>The full {@link io.fluxcapacitor.javaclient.common.Message} or its {@link io.fluxcapacitor.common.api.Metadata}</li>
 *     <li>Other context such as the {@link io.fluxcapacitor.javaclient.tracking.handling.authentication.User} performing the update</li>
 * </ul>
 *
 * <p>
 * Note that empty entities (where the value of the entity is {@code null}) are not injected unless the parameter
 * is annotated with {@code @Nullable}.
 *
 * <h2>Examples</h2>
 *
 * <h3>1. Creating a new entity from an @Apply method inside the update class</h3>
 * <pre>{@code
 * @Apply
 * Issue create() {
 *     return Issue.builder()
 *                 .issueId(issueId)
 *                 .count(1)
 *                 .status(IssueStatus.OPEN)
 *                 .details(issueDetails)
 *                 .firstSeen(lastSeen)
 *                 .lastSeen(lastSeen)
 *                 .build();
 * }
 * }</pre>
 *
 * <h3>2. Updating an entity with a new state</h3>
 * <pre>{@code
 * @Apply
 * Product apply(Product product) {
 *     return product.toBuilder().details(details).build();
 * }
 * }</pre>
 *
 * <h3>3. Deleting an entity</h3>
 * <pre>{@code
 * @Apply
 * Product apply(Product product) {
 *     return null;
 * }
 * }</pre>
 *
 * <h3>4. Defining apply methods inside the entity class</h3>
 * <pre>{@code
 * @Apply
 * static Product create(CreateProduct update) {
 *     return Product.builder()
 *                   .productId(update.getProductId())
 *                   .details(update.getDetails())
 *                   .build();
 * }
 *
 * @Apply
 * Product update(UpdateProduct update) {
 *     return this.toBuilder().details(update.getDetails()).build();
 * }
 *
 * @Apply
 * Product delete(DeleteProduct update) {
 *     return null;
 * }
 * }</pre>
 *
 * <h4>Routing example with aggregates and nested entities</h4>
 * <pre>{@code
 * @Aggregate
 * class ProductCategory {
 *     String categoryId;
 *
 *     @Member
 *     List<Product> products;
 * }
 * }</pre>
 * Updates targeting `Product` will automatically be routed based on `@EntityId` inside `Product`.
 *
 * @see io.fluxcapacitor.javaclient.modeling.Aggregate
 * @see EntityId
 * @see io.fluxcapacitor.javaclient.modeling.Member
 * @see EventPublication
 * @see EventPublicationStrategy
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Apply {

    /**
     * Controls whether the update should result in a published update, depending on whether the entity was actually
     * modified.
     * <p>
     * This overrides the default from the enclosing aggregate, if set.
     *
     * @return update publication behavior
     */
    EventPublication eventPublication() default EventPublication.DEFAULT;

    /**
     * Controls how the applied update is stored and/or published. This strategy takes precedence over
     * {@link #eventPublication()} if explicitly set.
     *
     * @return strategy for persisting and/or publishing the applied update
     */
    EventPublicationStrategy publicationStrategy() default EventPublicationStrategy.DEFAULT;
}

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

package io.fluxcapacitor.javaclient.persisting.repository;

import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.modeling.Id;
import lombok.NonNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for loading, managing, and repairing aggregates in Flux Capacitor.
 * <p>
 * Aggregates in Flux Capacitor can be persisted using different storage strategies, including event sourcing and
 * document-based storage (e.g., {@code DocumentStore}). The {@code AggregateRepository} abstracts these details and
 * provides a unified API to interact with aggregate instances as {@link Entity} wrappers.
 *
 * <p>Core capabilities include:
 * <ul>
 *   <li>Loading aggregates by ID, whether event-sourced or document-based.</li>
 *   <li>Mapping entities to their associated aggregates (e.g., for child-to-parent traversal).</li>
 *   <li>Repairing relationships between entities and aggregates (especially useful after structural refactors).</li>
 *   <li>Deleting aggregates and their associated data.</li>
 * </ul>
 *
 * <p>Note: While this interface is accessible directly, most typical usage is through the static
 * {@code FluxCapacitor.loadAggregate(...)} or via delegation
 * in {@link io.fluxcapacitor.javaclient.modeling.Entity} classes.
 */
public interface AggregateRepository {

    /**
     * Load an aggregate by a typed identifier.
     *
     * @param aggregateId the typed ID of the aggregate.
     * @param <T>         the aggregate type.
     * @return the aggregate as a loaded {@link Entity} wrapper.
     */
    default <T> Entity<T> load(Id<T> aggregateId) {
        return load(aggregateId.toString(), aggregateId.getType());
    }

    /**
     * Load an aggregate by its identifier and type.
     *
     * @param aggregateId   the aggregate identifier.
     * @param aggregateType the expected class type.
     * @param <T>           the aggregate type.
     * @return the loaded aggregate wrapped in an {@link Entity}.
     */
    <T> Entity<T> load(@NonNull Object aggregateId, Class<T> aggregateType);

    /**
     * Load the aggregate that owns the specified entity.
     * <p>
     * If no ownership is found in the relationship index, this method may fall back to loading the entity as if it were
     * an aggregate itself.
     *
     * @param entityId    the child or nested entity.
     * @param defaultType fallback type to use when no aggregate mapping is available.
     * @param <T>         the aggregate type.
     * @return the loaded aggregate as an {@link Entity}.
     */
    <T> Entity<T> loadFor(@NonNull Object entityId, Class<?> defaultType);

    /**
     * Wrap an existing aggregate instance into an {@link Entity}, initializing tracking and identity information.
     *
     * @param entity the aggregate instance.
     * @param <T>    the aggregate type.
     * @return the entity wrapper.
     */
    <T> Entity<T> asEntity(T entity);

    /**
     * Repairs the relationships of the aggregate corresponding to the given typed ID.
     *
     * @param aggregateId the typed identifier of the aggregate.
     * @return a future that completes when the repair process is done.
     */
    default CompletableFuture<Void> repairRelationships(Id<?> aggregateId) {
        return repairRelationships(load(aggregateId));
    }

    /**
     * Repairs the relationships of the aggregate with the given ID.
     *
     * @param aggregateId the ID of the aggregate.
     * @return a future that completes when the repair process is done.
     */
    default CompletableFuture<Void> repairRelationships(Object aggregateId) {
        return repairRelationships(load(aggregateId, Object.class));
    }

    /**
     * Repairs the internal relationship model for a loaded aggregate.
     * <p>
     * This is useful when refactoring entity hierarchies or recovering from inconsistent relationship state.
     *
     * @param aggregate the aggregate to inspect.
     * @return a future that completes when the relationships are updated.
     */
    CompletableFuture<Void> repairRelationships(Entity<?> aggregate);

    /**
     * Returns a map of aggregate IDs and their types that are associated with a given entity ID.
     *
     * @param entityId the child or nested entity.
     * @return a map of aggregate IDs to class names.
     */
    Map<String, Class<?>> getAggregatesFor(Object entityId);

    /**
     * Returns the most recently created aggregate ID associated with a given entity ID, if any.
     *
     * @param entityId the child or nested entity.
     * @return the latest aggregate ID, if present.
     */
    default Optional<String> getLatestAggregateId(Object entityId) {
        return getAggregatesFor(entityId).keySet().stream().reduce((a, b) -> b);
    }

    /**
     * Deletes the persisted state for an aggregate, including its events or document and relationships.
     *
     * @param aggregateId the ID of the aggregate to delete.
     * @return a future that completes when deletion has been confirmed.
     */
    CompletableFuture<Void> deleteAggregate(Object aggregateId);
}

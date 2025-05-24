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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the annotated field or getter represents a nested entity or collection of entities within an
 * aggregate.
 * <p>
 * Entities marked with {@code @Member} participate in aggregate routing, event sourcing, and update application. When
 * an update targets a nested entity, Flux will use this annotation to traverse the aggregate structure and locate the
 * correct entity (or entities) to apply the update to.
 *
 * <p>
 * This annotation supports modeling complex aggregates composed of multiple entities, for example:
 * <pre>{@code
 * @Aggregate
 * public class Project {
 *     @EntityId
 *     String projectId;
 *
 *     @Member
 *     List<Task> tasks;
 * }
 * }</pre>
 * Here, updates targeting {@code Task} entities will automatically be routed by matching {@code TaskId} (declared with
 * {@link io.fluxcapacitor.javaclient.modeling.EntityId}) inside the {@code Task} class.
 *
 * <h2>Support for new entities</h2>
 * <p>
 * If no matching entity is found for a given update, Flux will still evaluate the update against applicable
 * {@code @Apply} and {@code @AssertLegal} methods. This allows new entity creation directly from the update payload
 * when appropriate logic is defined.
 * <br>For example:
 * <pre>{@code
 * @Apply
 * Task create() {
 *     return Task.builder().taskId(taskId).details(taskDetails).build();
 * }
 * }</pre>
 * will be used to create a new {@code Task} if no matching task exists in the {@code tasks} member list.
 *
 * <h2>Immutability and parent updates</h2>
 * <p>
 * Flux assumes immutability by default. When a nested entity is added, removed, or modified, Flux will attempt to
 * create a new version of the parent entity by copying and updating the annotated container field (list, map, etc.).
 * The parent is not modified directly.
 * <br>This behavior ensures safe update propagation and accurate change tracking, especially during event sourcing.
 * <br>For example, if {@code ProductCategory} has a list of {@code Product}s:
 * <pre>{@code
 * @Member
 * List<Product> products;
 * }</pre>
 * and one product is updated, Flux will replace the {@code products} list with a new list containing the updated
 * entity.
 *
 * <h2>Optional attributes</h2>
 * <ul>
 *     <li><strong>{@code idProperty}</strong> (default: empty):<br>
 *         Use this to explicitly specify the identifier property name on the nested entity. By default,
 *         Flux locates the identifier via the {@link io.fluxcapacitor.javaclient.modeling.EntityId} annotation.</li>
 *
 *     <li><strong>{@code wither}</strong> (default: empty):<br>
 *         Defines a method (by name) that should be invoked to update the container when the entity is added,
 *         removed, or replaced. Normally, Flux will update the container (e.g., list or map) automatically.
 *         This setting is useful for immutable containers or cases requiring side effects during updates.
 *     </li>
 * </ul>
 *
 * <p>
 * Supported container types:
 * <ul>
 *     <li>Single nested entities (e.g., {@code Product product})</li>
 *     <li>Collections of entities (e.g., {@code List<Product>})</li>
 *     <li>Maps of entities keyed by their identifier</li>
 * </ul>
 *
 * @see io.fluxcapacitor.javaclient.modeling.EntityId
 * @see io.fluxcapacitor.javaclient.modeling.Aggregate
 * @see io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply
 * @see io.fluxcapacitor.javaclient.modeling.AssertLegal
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Member {

    /**
     * Specifies the name of the identifier property on the nested entity, if different from the default detected one.
     */
    String idProperty() default "";

    /**
     * Optionally defines the name of a method that should be used to apply updates to the container of the nested
     * entity.
     * <p>
     * Normally, Flux automatically updates the container (for lists, maps, or singletons). This attribute is only
     * necessary if a custom update method must be invoked instead.
     */
    String wither() default "";
}

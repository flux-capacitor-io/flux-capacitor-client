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

import io.fluxcapacitor.javaclient.modeling.AssertLegal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;

/**
 * Indicates that a method should intercept and potentially transform an update before it is applied to an entity.
 * <p>
 * This annotation is typically used to:
 * <ul>
 *     <li>Suppress updates that should be ignored</li>
 *     <li>Rewrite or correct invalid updates</li>
 *     <li>Split a single update into multiple updates</li>
 * </ul>
 * <p>
 * Interceptors are invoked <strong>before</strong> any {@link Apply @Apply} or {@link AssertLegal @AssertLegal}
 * methods. If multiple interceptors match, they are invoked recursively until the result stabilizes.
 *
 * <p>
 * Interceptors can return:
 * <ul>
 *     <li>The original update (no change)</li>
 *     <li>{@code null} or {@code void} to suppress the update</li>
 *     <li>An {@link java.util.Optional}, {@link Collection}, or {@link java.util.stream.Stream} to emit zero or more updates</li>
 *     <li>A different object to replace the update</li>
 * </ul>
 *
 * <p>
 * Method parameters are automatically injected and may include:
 * <ul>
 *     <li>The current entity (if it exists)</li>
 *     <li>Any parent or ancestor entity in the aggregate</li>
 *     <li>The update object (if defined on the entity side)</li>
 *     <li>Context like {@link io.fluxcapacitor.common.api.Metadata}, {@link io.fluxcapacitor.javaclient.common.Message}, or
 *         {@link io.fluxcapacitor.javaclient.tracking.handling.authentication.User}</li>
 * </ul>
 *
 * <p>
 * Note that empty entities (where the value is {@code null}) are not injected unless the parameter is annotated with
 * {@code @Nullable}.
 *
 * <h2>Examples</h2>
 *
 * <h3>1. Rewrite a duplicate create into an update (inside the update class)</h3>
 * <pre>{@code
 * @InterceptApply
 * UpdateProject resolveDuplicateCreate(Project project) {
 *     // If this method is invoked, the Project already exists
 *     return new UpdateProject(projectId, details);
 * }
 * }</pre>
 *
 * <h3>2. Suppress a no-op update</h3>
 * <pre>{@code
 * @InterceptApply
 * Object ignoreNoChange(Product product) {
 *     if (product.getDetails().equals(details)) {
 *         return null; // suppress update
 *     }
 *     return this;
 * }
 * }</pre>
 *
 * <p><strong>Note:</strong> You typically do <em>not</em> need to implement this kind of check manually if the
 * enclosing {@link io.fluxcapacitor.javaclient.modeling.Aggregate @Aggregate} or specific
 * {@link Apply @Apply} method is configured with
 * {@link io.fluxcapacitor.javaclient.modeling.EventPublication#IF_MODIFIED IF_MODIFIED}.
 * That configuration ensures that no event is stored or published if the entity is not modified.
 *
 * <h3>3. Expand a bulk update into individual operations</h3>
 * <pre>{@code
 * @InterceptApply
 * List<CreateTask> explodeBulkCreate() {
 *     return tasks;
 * }
 * }</pre>
 *
 * <h3>4. Recursive interception</h3>
 * <p>
 * If the result of one {@code @InterceptApply} method is a new update object, Flux will look for matching
 * interceptors for the new value as well — continuing recursively until no further changes occur.
 *
 * @see Apply
 * @see AssertLegal
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface InterceptApply {
}

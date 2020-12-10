/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field or method level annotation used in parent entities that marks a field or method as the provider of child
 * entities.
 * <p>
 * The annotation can be placed on properties that refer to a single entity, a collection of entities, or a map of
 * entities (mapped by their entity id).
 * <p>
 * To make sure the entities can be updated, an appropriate method should be present for the annotated property. Update
 * methods will be automatically found if they have one the following signatures:
 * <ul>
 *     <li>Parent withChild(Child child)</li>
 *     <li>Parent child(Child child)</li>
 *     <li>void setChild(Child child)</li>
 *     <li>void child(Child child)</li>
 * </ul>
 * <p>
 * In case of Collection (same goes for Map) this works the same way:
 *
 * <ul>
 *     <li>Parent withChildren(List children)</li>
 *     <li>Parent children(List children)</li>
 *     <li>void setChildren(List children)</li>
 *     <li>void children(List children)</li>
 * </ul>
 *
 * <p>
 * To use a different method for updating use {@link #updateMethod()}. A runtime exception will be thrown in case no suitable
 * update method can be found when the entity is first scanned.
 * <p>
 * A property for the entity's id can be configured using {@link #entityId()}. If this is left empty the id
 * is assumed to spring from a property on the entity annotated with {@link EntityId}. If the entity property cannot be
 * determined a runtime exception is thrown when the entity is first scanned.
 */
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Entity {
    String updateMethod() default "";

    String entityId() default "";
}

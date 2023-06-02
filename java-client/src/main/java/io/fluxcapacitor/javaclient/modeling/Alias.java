/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used on properties of an entity value that provide an alternative identifier to find the entity.
 * <p>
 * Finding an entity by alias is used both when looking for a child entity via {@link Entity#getEntity}, and when
 * loading an entity (or aggregate) by entity id, e.g. using {@link FluxCapacitor#loadAggregateFor}.
 * <p>
 * You can annotate fields and property methods. If a property value is a collection the members of the collection are
 * all added as aliases of the entity. If the property value is {@code null} or an empty collection the alias is
 * ignored.
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Alias {
}

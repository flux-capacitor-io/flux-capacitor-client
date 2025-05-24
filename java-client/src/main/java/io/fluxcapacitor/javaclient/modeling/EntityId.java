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
 * Marks a property (field or getter) as the unique identifier of an entity within an aggregate structure.
 * <p>
 * The presence of this annotation enables automatic routing of updates to the correct entity instance inside an
 * aggregate, based on identifier matching.
 * <p>
 * This is particularly important in aggregates that consist of nested entities, e.g.: a {@code Project}
 * containing a list of {@code Task} entities. The framework uses the {@code @EntityId}-annotated property to match
 * update messages with their corresponding entity.
 * <p>
 * You can annotate either a field or its getter method.
 *
 * @see Aggregate for how to define aggregates and their structure.
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface EntityId {
}

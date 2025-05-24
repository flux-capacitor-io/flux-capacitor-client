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

package io.fluxcapacitor.javaclient.tracking.handling.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies validation groups to apply when validating the annotated class.
 * <p>
 * This annotation can be placed on payload classes (such as updates or commands) or on any class
 * that is validated manually using {@link io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils}.
 * <p>
 * When present, only the specified validation groups will be used. If no groups are defined
 * or if the annotation is missing altogether, validation is performed using the default group
 * ({@link jakarta.validation.groups.Default}).
 *
 * <p><strong>Example:</strong></p>
 * <pre>{@code
 * @ValidateWith(AdminChecks.class)
 * public class ApproveApplication {
 *
 *     @NotNull(groups = AdminChecks.class)
 *     private String approvalNote;
 * }
 * }</pre>
 *
 * <p>
 * This allows you to tailor validation rules depending on context, such as admin-specific checks,
 * creation vs. update flows, or conditional enforcement.
 *
 * @see jakarta.validation.GroupSequence
 * @see jakarta.validation.groups.Default
 * @see io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ValidateWith {
    /**
     * One or more validation groups to include when validating this class.
     *
     * @return the groups to use during validation
     */
    Class<?>[] value();
}

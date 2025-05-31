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

package io.fluxcapacitor.javaclient.configuration.spring;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;

/**
 * Callback interface that can be implemented by Spring beans to customize the {@link FluxCapacitorBuilder}
 * before it is used to build the main {@link FluxCapacitor} instance.
 * <p>
 * This allows applications to modularly apply configuration logic, such as registering additional components,
 * setting default values, or modifying behaviors.
 *
 * <p>For example:
 * <pre>{@code
 * @Component
 * public class MyCustomizer implements FluxCapacitorCustomizer {
 *     @Override
 *     public FluxCapacitorBuilder customize(FluxCapacitorBuilder builder) {
 *         return builder.addParameterResolver(new CustomResolver());
 *     }
 * }
 * }</pre>
 *
 * @see FluxCapacitorBuilder
 * @see FluxCapacitor
 */
@FunctionalInterface
public interface FluxCapacitorCustomizer {
    FluxCapacitorBuilder customize(FluxCapacitorBuilder builder);
}
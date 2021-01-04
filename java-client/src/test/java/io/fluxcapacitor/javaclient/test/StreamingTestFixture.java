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

package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.configuration.client.Client;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public class StreamingTestFixture extends AbstractTestFixture {

    public static StreamingTestFixture create(Object... handlers) {
        return new StreamingTestFixture(fc -> Arrays.asList(handlers));
    }

    public static StreamingTestFixture create(FluxCapacitorBuilder fluxCapacitorBuilder, Object... handlers) {
        return new StreamingTestFixture(fluxCapacitorBuilder, fc -> Arrays.asList(handlers));
    }

    public static StreamingTestFixture create(Function<FluxCapacitor, List<?>> handlersFactory) {
        return new StreamingTestFixture(handlersFactory);
    }

    public static StreamingTestFixture create(FluxCapacitorBuilder fluxCapacitorBuilder, Function<FluxCapacitor, List<?>> handlersFactory) {
        return new StreamingTestFixture(fluxCapacitorBuilder, handlersFactory);
    }

    public static StreamingTestFixture create(FluxCapacitorBuilder fluxCapacitorBuilder, Client client, Object... handlers) {
        return new StreamingTestFixture(fluxCapacitorBuilder, fc -> Arrays.asList(handlers), client);
    }

    protected StreamingTestFixture(Function<FluxCapacitor, List<?>> handlersFactory) {
        super(handlersFactory);
    }

    protected StreamingTestFixture(FluxCapacitorBuilder fluxCapacitorBuilder, Function<FluxCapacitor, List<?>> handlersFactory) {
        super(fluxCapacitorBuilder, handlersFactory);
    }

    protected StreamingTestFixture(FluxCapacitorBuilder fluxCapacitorBuilder, Function<FluxCapacitor, List<?>> handlersFactory, Client client) {
        super(fluxCapacitorBuilder, handlersFactory, client);
    }

    @Override
    public Registration registerHandlers(List<?> handlers) {
        return getFluxCapacitor().registerHandlers(handlers);
    }
}

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

import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.util.Optional;
import java.util.stream.Stream;

public interface EntityHelper {
    Stream<?> intercept(Object value, Entity<?> entity);
    Optional<HandlerInvoker> applyInvoker(DeserializingMessage message, Entity<?> entity);
    <E extends Exception> void assertLegal(Object value, Entity<?> entity) throws E;
    <E extends Exception> Optional<E> checkLegality(Object value, Entity<?> entity);
    boolean isLegal(Object value, Entity<?> entity);
}

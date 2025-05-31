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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.reflection.ReflectionUtils;

import java.beans.Transient;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Marker interface for request messages (e.g., commands or queries) that expect a response of a specific type.
 * <p>
 * Implementing this interface allows the Flux Capacitor client to infer the return type of a request, enabling:
 * <ol>
 *     <li>Type-safe request invocations (e.g. {@code FluxCapacitor.queryAndWait(Request<T>)} returns {@code T})</li>
 *     <li>Compile-time verification that handler methods return a value compatible with the expected response</li>
 * </ol>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @Value
 * public class GetUser implements Request<UserProfile> {
 *     String userId;
 *
 *     @HandleQuery
 *     UserProfile handle(GetUser query) {
 *         return FluxCapacitor.search(UserProfile.class).match(query.getUserId()).fetchFirstOrNull();
 *     }
 * }
 * }</pre>
 *
 * <p>
 * The {@link #responseType()} method provides runtime introspection for the expected return type. This is primarily
 * used for generic resolution and handler validation, and relies on reflection to inspect the generic type parameter
 * {@code R} declared in {@code Request<R>}.
 *
 * @param <R> the expected response type of the request
 */
@SuppressWarnings("unused")
public interface Request<R> {

    /**
     * Returns the expected response type associated with this request instance.
     * <p>
     * This is resolved via reflective analysis of the request's declared generic type (i.e., {@code R} in
     * {@code Request<R>}). If no concrete type is available, {@code Object.class} is returned as a fallback.
     *
     * @return the {@link Type} representing the expected response type, or {@code Object.class} if unknown
     */
    @Transient
    default Type responseType() {
        Type genericType = ReflectionUtils.getGenericType(getClass(), Request.class);
        if (genericType instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1) {
            return pt.getActualTypeArguments()[0];
        }
        return Object.class;
    }
}

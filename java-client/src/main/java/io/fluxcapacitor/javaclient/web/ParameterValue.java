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

package io.fluxcapacitor.javaclient.web;

import lombok.Value;

/**
 * Wrapper around a {@link io.jooby.Value} representing a resolved parameter value in a web request.
 * <p>
 * This class is used internally by Flux Capacitor to abstract over raw parameter values retrieved
 * from different parts of a {@link WebRequestContext} (such as query strings, form data, path variables,
 * headers, or cookies).
 *
 * <p>The value is backed by Jooby’s {@link io.jooby.Value} type, which provides flexible conversion and
 * null-safe access to typed values.
 *
 * <h2>Usage</h2>
 * {@code ParameterValue} instances are returned by methods such as:
 * <ul>
 *   <li>{@link WebRequestContext#getParameter(String, WebParameterSource...)}</li>
 *   <li>{@link WebRequestContext#getQueryParameter(String)}</li>
 *   <li>{@link WebRequestContext#getFormParameter(String)}</li>
 * </ul>
 * These are used during web handler method resolution to supply method argument values.
 *
 * @see WebRequestContext
 * @see io.jooby.Value
 */
@Value
public class ParameterValue {

    /**
     * The underlying Jooby {@link io.jooby.Value} representing the parameter.
     */
    io.jooby.Value value;

    /**
     * Converts the underlying value to the specified target type.
     * <p>
     * If conversion fails or the value is not present, {@code null} is returned.
     *
     * @param type the desired target type
     * @param <V>  the generic type to cast to
     * @return the converted value or {@code null} if unavailable
     */
    public <V> V as(Class<V> type) {
        return value.toNullable(type);
    }
}

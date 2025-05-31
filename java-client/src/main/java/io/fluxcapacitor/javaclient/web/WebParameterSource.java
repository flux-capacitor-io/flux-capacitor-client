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

/**
 * Enumerates the sources of parameter values in an HTTP or WebSocket request.
 * <p>
 * This enum is used by {@link WebParam} and its derived annotations (e.g. {@link PathParam}, {@link QueryParam}) to
 * specify where the value should be extracted from.
 * </p>
 *
 * <ul>
 *   <li>{@link #PATH} – Extracted from URI template variables (e.g., {@code /user/{id}})</li>
 *   <li>{@link #QUERY} – Extracted from the query string (e.g., {@code ?id=123})</li>
 *   <li>{@link #HEADER} – Extracted from HTTP headers</li>
 *   <li>{@link #COOKIE} – Extracted from cookies</li>
 *   <li>{@link #FORM} – Extracted from form fields (URL-encoded or multipart)</li>
 * </ul>
 *
 * @see WebParam
 */
public enum WebParameterSource {
    /**
     * URI path parameters (e.g. /items/{id})
     */
    PATH,

    /**
     * HTTP query string parameters (e.g. ?search=abc)
     */
    QUERY,

    /**
     * HTTP request headers
     */
    HEADER,

    /**
     * Cookies sent in the request
     */
    COOKIE,

    /**
     * Form fields (application/x-www-form-urlencoded or multipart/form-data)
     */
    FORM
}

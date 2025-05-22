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
 * Interface representing a context for handling web requests. It provides methods for accessing parameter values
 * from various sources such as path, query, form, headers, and cookies.
 */
public interface WebRequestContext {

    /**
     * Retrieves the value of a specified parameter from the given sources in a web request.
     *
     * @param name the name of the parameter to retrieve. Must not be null.
     * @param sources the sources to look for the parameter, such as PATH, QUERY, HEADER, COOKIE, or FORM.
     *                If no sources are provided, the method may default to a predefined set of sources.
     * @return the {@link ParameterValue} associated with the specified parameter name and sources,
     *         or an empty {@link ParameterValue} if the parameter is not found.
     */
    ParameterValue getParameter(String name, WebParameterSource... sources);

    /**
     * Retrieves the value of a parameter from the web request. This method searches for the specified parameter
     * in the default parameter sources: path, query, and form data.
     *
     * @param name the name of the parameter to retrieve
     * @return the value of the parameter, wrapped in a {@code ParameterValue}, or {@code null} if the parameter is not found
     */
    default ParameterValue getParameter(String name) {
        return getParameter(name, WebParameterSource.PATH, WebParameterSource.QUERY, WebParameterSource.FORM);
    }

    /**
     * Retrieves the value of a parameter from the path section of the web request.
     *
     * @param name the name of the parameter to retrieve from the path.
     * @return the {@code ParameterValue} associated with the specified parameter name, or {@code null}
     *         if the parameter is not present in the path.
     */
    default ParameterValue getPathParameter(String name) {
        return getParameter(name, WebParameterSource.PATH);
    }

    /**
     * Retrieves the value of a query parameter from the web request.
     *
     * @param name the name of the query parameter to retrieve
     * @return the {@code ParameterValue} representing the value of the query parameter, or {@code null} if the parameter is not present
     */
    default ParameterValue getQueryParameter(String name) {
        return getParameter(name, WebParameterSource.QUERY);
    }

    /**
     * Retrieves the value of a parameter from the HTTP request headers by its name.
     *
     * @param name the name of the parameter to retrieve from the header
     * @return the value of the header parameter wrapped in a {@code ParameterValue}, or {@code null} if not found
     */
    default ParameterValue getHeaderParameter(String name) {
        return getParameter(name, WebParameterSource.HEADER);
    }

    /**
     * Retrieves a parameter value from the cookie source using the specified parameter name.
     *
     * @param name the name of the cookie parameter to retrieve
     * @return the value of the specified cookie parameter wrapped in a {@code ParameterValue} object
     */
    default ParameterValue getCookieParameter(String name) {
        return getParameter(name, WebParameterSource.COOKIE);
    }

    /**
     * Retrieves the value of a form parameter from the web request context.
     *
     * @param name the name of the form parameter to retrieve.
     * @return the value of the form parameter as a {@code ParameterValue},
     *         or {@code null} if the parameter is not present in the form source.
     */
    default ParameterValue getFormParameter(String name) {
        return getParameter(name, WebParameterSource.FORM);
    }

}

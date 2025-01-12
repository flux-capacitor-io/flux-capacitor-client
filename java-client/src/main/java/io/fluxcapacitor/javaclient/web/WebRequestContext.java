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

public interface WebRequestContext {

    ParameterValue getParameter(String name, WebParameterSource... sources);

    default ParameterValue getParameter(String name) {
        return getParameter(name, WebParameterSource.PATH, WebParameterSource.QUERY, WebParameterSource.FORM);
    }

    default ParameterValue getPathParameter(String name) {
        return getParameter(name, WebParameterSource.PATH);
    }

    default ParameterValue getQueryParameter(String name) {
        return getParameter(name, WebParameterSource.QUERY);
    }

    default ParameterValue getHeaderParameter(String name) {
        return getParameter(name, WebParameterSource.HEADER);
    }

    default ParameterValue getCookieParameter(String name) {
        return getParameter(name, WebParameterSource.COOKIE);
    }

    default ParameterValue getFormParameter(String name) {
        return getParameter(name, WebParameterSource.FORM);
    }

}

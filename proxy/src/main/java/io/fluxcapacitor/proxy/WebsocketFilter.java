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

package io.fluxcapacitor.proxy;

import io.fluxcapacitor.javaclient.web.HttpRequestMethod;
import io.fluxcapacitor.javaclient.web.WebRequest;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static io.fluxcapacitor.javaclient.web.WebUtils.fixHeaderName;
import static io.fluxcapacitor.proxy.WebsocketEndpoint.metadataPrefix;

@Slf4j
public class WebsocketFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest r = (HttpServletRequest) request;
        chain.doFilter(new HttpServletRequestWrapper(r) {
            @Override
            public Map<String, String[]> getParameterMap() {
                var result = new HashMap<>(r.getParameterMap());
                {
                    var builder = WebRequest.builder()
                            .url(r.getServletPath() + (r.getQueryString() == null ? "" : ("?" + r.getQueryString())))
                            .method(HttpRequestMethod.valueOf(r.getMethod()));
                    r.getHeaderNames().asIterator().forEachRemaining(
                            name -> r.getHeaders(name).asIterator().forEachRemaining(
                                    value -> builder.header(fixHeaderName(name), value)));
                    builder.build().getMetadata().getEntries().forEach(
                            (k, v) -> result.put(metadataPrefix + k, new String[]{v}));
                }
                return result;
            }
        }, response);
    }
}

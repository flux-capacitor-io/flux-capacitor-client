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
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

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

    protected String fixHeaderName(String name) {
        return Arrays.stream(name.split("-")).map(StringUtils::capitalize).collect(Collectors.joining("-"));
    }
}

package io.fluxcapacitor.javaclient.web;

import lombok.NonNull;

import java.lang.reflect.Executable;
import java.net.HttpCookie;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotationAs;
import static org.apache.commons.lang3.StringUtils.isBlank;

public class WebUtils {

    public static String toString(@NonNull HttpCookie cookie) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookie.getName()).append("=").append(URLEncoder.encode(cookie.getValue(), StandardCharsets.UTF_8));
        if (!isBlank(cookie.getDomain())) {
            sb.append("; ").append("Domain=").append(cookie.getDomain());
        }
        if (!isBlank(cookie.getPath())) {
            sb.append("; ").append("Path=").append(cookie.getPath());
        }
        if (cookie.getMaxAge() != -1) {
            sb.append("; ").append("Max-Age=").append(cookie.getMaxAge());
        }
        if (cookie.isHttpOnly()) {
            sb.append("; ").append("HttpOnly");
        }
        if (cookie.getSecure()) {
            sb.append("; ").append("Secure");
        }
        return sb.toString();
    }

    public static List<HttpCookie> parseRequestCookieHeader(String cookieHeader) {
        return cookieHeader == null ? List.of() : Arrays.stream(cookieHeader.split(";")).map(c -> {
            var parts = c.trim().split("=");
            return new HttpCookie(parts[0].trim(), parts[1].trim().replaceAll("^\"|\"$", ""));
        }).collect(Collectors.toList());
    }

    public static List<HttpCookie> parseResponseCookieHeader(List<String> setCookieHeaders) {
        return setCookieHeaders == null ? List.of()
                : setCookieHeaders.stream().flatMap(h -> HttpCookie.parse(h).stream()).toList();
    }

    public static Optional<WebParameters> getWebParameters(Executable method) {
        return getAnnotationAs(method, HandleWeb.class, WebParameters.class);
    }
}

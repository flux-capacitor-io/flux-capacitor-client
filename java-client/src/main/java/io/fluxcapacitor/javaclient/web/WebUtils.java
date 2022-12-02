package io.fluxcapacitor.javaclient.web;

import java.net.HttpCookie;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.apache.commons.lang3.StringUtils.isBlank;

public class WebUtils {

    public static String toString(HttpCookie cookie) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookie.getName()).append("=").append(URLEncoder.encode(cookie.getValue(), StandardCharsets.UTF_8));
        if (!isBlank(cookie.getDomain())) {
            sb.append("; ").append("Domain=").append(cookie.getDomain());
        }
        if (!isBlank(cookie.getPath())) {
            sb.append("; ").append("Path=").append(cookie.getDomain());
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

}

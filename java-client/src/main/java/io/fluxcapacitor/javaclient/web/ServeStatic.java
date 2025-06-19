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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a static file handler that serves files from a resource or file system location at the specified web path(s).
 * <p>
 * This annotation can be placed on a class or package that configures or instantiates a {@code StaticFileHandler}. At runtime,
 * it may be discovered and used to register routes that serve static assets such as HTML, JavaScript, CSS, or images.
 * <p>
 * Files are served from either the file system or the classpath. If a file exists in both locations, the file system version
 * takes precedence. If the {@code resourcePath} starts with {@code classpath:}, it is <strong>only</strong> loaded from the
 * classpath. If it starts with {@code file:}, it is <strong>only</strong> loaded from the file system.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
public @interface ServeStatic {

    /**
     * One or more web path patterns this handler applies to (e.g. {@code "static"}, {@code "/web/public"}).
     * <p>
     * If a path starts with {@code /}, it is interpreted as an absolute path and not influenced by outer {@link Path} annotations.
     * Otherwise, it is considered relative and will be prefixed with any outer {@code @Path} values.
     * <p>
     * If left empty, the path will be inferred from outer {@code @Path} annotations or defaults.
     *
     * @return Web path patterns for serving static content.
     */
    String[] value() default {};

    /**
     * The base resource path from which to serve static files.
     * <p>
     * May point to a classpath directory (e.g., {@code /static}, {@code assets/}) or a file system path (e.g., {@code /opt/myapp/public}).
     * If both classpath and file system contain the same file, the file system version takes precedence.
     * <p>
     * Prefixing the path with {@code classpath:} or {@code file:} restricts loading to that source only.
     *
     * @return Path to the directory containing static resources.
     */
    String resourcePath() default "/static";

    /**
     * Optional fallback file to serve if a requested resource is not found.
     * <p>
     * This is useful for single-page applications (SPAs) where all unknown routes should fall back to {@code index.html}.
     * <p>
     * To disable this behavior, return an empty string.
     *
     * @return Relative path to the fallback file, e.g., {@code index.html}.
     */
    String fallbackFile() default "index.html";

    /**
     * File extensions considered candidates for immutable caching when their filenames are fingerprinted.
     * <p>
     * Fingerprinted files (e.g., {@code app.abc123.js}) with one of these extensions will receive
     * {@code Cache-Control: public, max-age=31536000, immutable}.
     *
     * @return Extensions (without leading dots) eligible for immutable cache headers.
     */
    String[] immutableCandidateExtensions() default {"html", "js", "css", "json", "svg"};

    /**
     * Default max-age (in seconds) for non-immutable static files.
     * <p>
     * Files that are not fingerprinted will receive a cache-control header such as
     * {@code Cache-Control: public, max-age=86400}. HTML files may override this with {@code must-revalidate}.
     *
     * @return Number of seconds the file is allowed to be cached.
     */
    int maxAgeSeconds() default 86400;
}
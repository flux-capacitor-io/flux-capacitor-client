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

import io.fluxcapacitor.common.reflection.ReflectionUtils;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class StaticFileHandler implements Closeable {

    private static final Map<URI, FileSystem> jarFileSystemCache = new ConcurrentHashMap<>();

    public static final String classpathPrefix = "classpath:";
    public static final String filePrefix = "file:";

    public static List<StaticFileHandler> forTargetClass(Class<?> targetClass) {
        ServeStatic serveStatic = ReflectionUtils.getAnnotation(targetClass, ServeStatic.class).orElse(null);
        if (serveStatic == null) {
            return List.of();
        }
        String path = WebUtils.getHandlerPath(targetClass, null, null);
        var webPaths = Arrays.stream(serveStatic.value())
                .map(v -> v.startsWith("/") ? v : WebUtils.concatenateUrlParts(path, v)).toList();
        if (webPaths.isEmpty()) {
            webPaths = Optional.of(path).filter(p -> !p.isBlank()).map(List::of).orElseThrow(
                    () -> new IllegalStateException("@ServeStatic's value should be present on " + targetClass.getName()
                                                    + " or @Path should be present on the target class or its package."));
        }
        return webPaths.stream()
                .map(webPath -> new StaticFileHandler(webPath, serveStatic.resourcePath(), serveStatic.fallbackFile(),
                                                      Arrays.stream(serveStatic.immutableCandidateExtensions())
                                                              .collect(Collectors.toSet()),
                                                      serveStatic.maxAgeSeconds())).toList();
    }

    public static boolean isHandler(Class<?> targetClass) {
        return ReflectionUtils.getAnnotation(targetClass, ServeStatic.class).isPresent();
    }

    @io.fluxcapacitor.javaclient.web.Path("")
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final String webRoot;

    private final @Nullable Path fsBaseDirectory;
    private final @Nullable URI resourceBaseUri;
    private final @Nullable String fallbackFile;
    private final Set<String> immutableCandidateExtensions;
    private final long maxAgeSeconds;

    public StaticFileHandler() {
        this("static");
    }

    public StaticFileHandler(String webRoot) {
        this(webRoot, "static");
    }

    public StaticFileHandler(String webRoot, String resourceBasePath) {
        this(webRoot, resourceBasePath, null);
    }

    public StaticFileHandler(String webRoot, String resourceBasePath, @Nullable String fallbackFile) {
        this(webRoot, resourceBasePath, fallbackFile, Set.of("html", "js", "css", "json", "svg"));
    }

    public StaticFileHandler(String webRoot, String resourceBasePath, @Nullable String fallbackFile,
                             Set<String> immutableCandidateExtensions) {
        this(webRoot, resourceBasePath, fallbackFile, immutableCandidateExtensions, 86400L);
    }

    public StaticFileHandler(String webRoot, String resourceBasePath, @Nullable String fallbackFile,
                             Set<String> immutableCandidateExtensions, long maxAgeSeconds) {
        this(webRoot, Optional.ofNullable(getFileSystemUri(resourceBasePath)).map(Paths::get).orElse(null),
             getResourceBaseUri(resourceBasePath), fallbackFile, immutableCandidateExtensions, maxAgeSeconds);
    }

    public StaticFileHandler(@NonNull String webRoot, @Nullable Path fsBaseDirectory, @Nullable URI resourceBaseUri,
                             @Nullable String fallbackFile, Set<String> immutableCandidateExtensions,
                             long maxAgeSeconds) {
        if (fallbackFile != null) {
            if (fallbackFile.isBlank()) {
                fallbackFile = null;
            } else if (fallbackFile.contains("..") || fallbackFile.contains("~") || fallbackFile.contains("\\")) {
                throw new IllegalArgumentException("Invalid fallback file: " + fallbackFile);
            }
        }
        if (fsBaseDirectory == null && resourceBaseUri == null) {
            throw new IllegalArgumentException("Either baseDirectory and resourceBaseUri should be provided");
        }
        if (maxAgeSeconds < 0) {
            log.warn("Max age seconds was negative which is invalid. Resetting to zero");
            maxAgeSeconds = 0L;
        }
        this.webRoot = webRoot.startsWith("/") ? webRoot.substring(1) : webRoot;
        this.fsBaseDirectory = fsBaseDirectory;
        this.resourceBaseUri = resourceBaseUri;
        this.fallbackFile = fallbackFile;
        this.immutableCandidateExtensions = immutableCandidateExtensions;
        this.maxAgeSeconds = maxAgeSeconds;
    }

    @HandleGet({"/{filePath:.+}*", "", "/"})
    @SneakyThrows
    protected WebResponse serveStaticFile(@PathParam("filePath") String filePath, WebRequest request) {
        Path resolvedPath = resolveSecurePath(filePath);

        if (resolvedPath == null) {
            return WebResponse.notFound("File not found");
        }

        String acceptEncoding = request.getHeader("Accept-Encoding");
        String rangeHeader = request.getHeader("Range");

        Compression compression = rangeHeader == null
                ? Compression.negotiate(acceptEncoding, resolvedPath)
                : Compression.NONE;

        Path actualPath = compression == Compression.NONE ? resolvedPath
                : resolvedPath.resolveSibling(resolvedPath.getFileName() + compression.extension);

        if (!Files.exists(actualPath)) {
            return WebResponse.notFound("File not found");
        }

        long fileSize = Files.size(actualPath);
        FileTime lastModifiedTime = Files.getLastModifiedTime(actualPath);
        String lastModified = formatHttpDate(lastModifiedTime.toMillis());
        String eTag = "\"" + lastModifiedTime.toMillis() + "-" + fileSize + "\"";

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept-Ranges", "bytes");
        headers.put("Content-Type", Files.probeContentType(resolvedPath));
        headers.put("Last-Modified", lastModified);
        headers.put("ETag", eTag);
        headers.put("Cache-Control", getCacheControlHeader(resolvedPath));
        headers.put("Vary", "Accept-Encoding");

        if (eTag.equals(request.getHeader("If-None-Match")) ||
            lastModified.equalsIgnoreCase(request.getHeader("If-Modified-Since"))) {
            return WebResponse.notModified(headers);
        }

        ByteRange range = parseRange(rangeHeader, fileSize);
        if (range == null) {
            if (compression != Compression.NONE) {
                headers.put("Content-Encoding", compression.encoding);
            }
            headers.put("Content-Length", String.valueOf(fileSize));
            return WebResponse.ok(() -> Files.newInputStream(actualPath), headers);
        } else {
            headers.put("Content-Range", "bytes " + range.start + "-" + range.end + "/" + fileSize);
            headers.put("Content-Length", String.valueOf(range.length()));
            return WebResponse.partial(() -> new PartialFileInputStream(actualPath, range.start(), range.length()),
                                       headers);
        }
    }

    @SneakyThrows
    private static URI getFileSystemUri(String resourceBasePath) {
        if (resourceBasePath.startsWith(classpathPrefix)) {
            return null;
        }
        if (!resourceBasePath.startsWith(filePrefix)) {
            resourceBasePath = filePrefix + resourceBasePath;
        }
        return URI.create(resourceBasePath);
    }

    @SneakyThrows
    private static URI getResourceBaseUri(String resourcePath) {
        if (resourcePath.startsWith(filePrefix)) {
            return null;
        }
        if (resourcePath.startsWith(classpathPrefix)) {
            resourcePath = resourcePath.substring(classpathPrefix.length());
        }
        String normalized = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        URL resourceUrl = StaticFileHandler.class.getClassLoader().getResource(normalized);
        if (resourceUrl == null) {
            return null;
        }
        return resourceUrl.toURI();
    }

    private static URI safeResolve(URI base, String relativePath) {
        if ("jar".equals(base.getScheme())) {
            int sep = base.getRawSchemeSpecificPart().indexOf("!/");
            if (sep != -1) {
                String jarRoot = base.getRawSchemeSpecificPart().substring(0, sep);
                String jarEntry = base.getRawSchemeSpecificPart().substring(sep + 2);
                URI newEntry = URI.create(jarEntry + "/").resolve(relativePath);
                return URI.create("jar:" + jarRoot + "!/" + newEntry);
            }
        }
        return base.resolve(relativePath);
    }

    private static URI getJarRootUri(URI jarResourceUri) {
        String raw = jarResourceUri.getRawSchemeSpecificPart();
        int sep = raw.indexOf("!/");
        if (sep != -1) {
            return URI.create(raw.substring(0, sep));
        }
        throw new IllegalArgumentException("Invalid jar URI: " + jarResourceUri);
    }

    private Path resolveSecurePath(String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()
            || requestedPath.contains("..") || requestedPath.contains("~") || requestedPath.contains("\\")) {
            if (fallbackFile != null) {
                return resolveSecurePath(fallbackFile);
            }
            return null;
        }

        // 1. Try file system first
        if (fsBaseDirectory != null) {
            Path fsPath = fsBaseDirectory.resolve(requestedPath).normalize();
            if (Files.exists(fsPath) && fsPath.startsWith(fsBaseDirectory)) {
                return fsPath;
            }
        }

        // 2. Try resolving from classpath URI
        if (resourceBaseUri != null) {
            try {
                URI resourceUri = safeResolve(resourceBaseUri, requestedPath);
                Path resourcePath;

                // Handle Spring Boot nested JARs
                if ("jar".equals(resourceUri.getScheme()) && resourceUri.getSchemeSpecificPart().startsWith("nested:")) {
                    String basePath = extractEmbeddedResourcePath(resourceBaseUri);
                    String fallbackPath = WebUtils.concatenateUrlParts(basePath, requestedPath);
                    URL resourceUrl = getClass().getClassLoader().getResource(fallbackPath);
                    if (resourceUrl != null) {
                        try {
                            resourcePath = Paths.get(resourceUrl.toURI());
                            if (Files.exists(resourcePath)) {
                                return resourcePath;
                            }
                        } catch (Exception e) {
                            log.warn("Failed to load resource from classloader path {}", fallbackPath, e);
                        }
                    }
                } else if ("jar".equals(resourceUri.getScheme())) {
                    // Regular JAR
                    URI jarRoot = getJarRootUri(resourceUri);
                    @SuppressWarnings("resource")
                    FileSystem fs = jarFileSystemCache.computeIfAbsent(jarRoot, uri -> {
                        try {
                            return FileSystems.newFileSystem(uri, Collections.emptyMap());
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to create FileSystem for " + uri, e);
                        }
                    });
                    resourcePath = fs.provider().getPath(resourceUri);
                    if (Files.exists(resourcePath)) {
                        return resourcePath;
                    }
                } else {
                    // Normal file URL or fallback
                    resourcePath = Paths.get(resourceUri);
                    if (Files.exists(resourcePath)) {
                        return resourcePath;
                    }
                }

            } catch (Exception e) {
                log.warn("Failed to resolve resource path: {}", requestedPath, e);
            }
        }

        // 3. Try fallback file if configured
        if (fallbackFile != null && !Objects.equals(requestedPath, fallbackFile)) {
            return resolveSecurePath(fallbackFile);
        }

        return null;
    }

    private static String extractEmbeddedResourcePath(URI resourceUri) {
        String rawPath = Optional.ofNullable(resourceUri.getPath())
                .orElse(resourceUri.getSchemeSpecificPart());

        // In case of nested URIs, strip leading parts before last !/
        int lastBang = rawPath.lastIndexOf("!/");
        if (lastBang >= 0) {
            return rawPath.substring(lastBang + 2); // skip "!/"
        }
        return rawPath;
    }

    private static ByteRange parseRange(String header, long fileSize) {
        if (header == null || !header.startsWith("bytes=")) {
            return null;
        }
        String[] parts = header.substring(6).split("-");
        if (parts.length != 2) {
            return null;
        }
        try {
            long start = parts[0].isEmpty() ? -1 : Long.parseLong(parts[0]);
            long end = parts[1].isEmpty() ? -1 : Long.parseLong(parts[1]);
            if (start == -1) {
                start = fileSize - end;
                end = fileSize - 1;
            } else if (end == -1 || end >= fileSize) {
                end = fileSize - 1;
            }
            return (start < 0 || end < start || end >= fileSize) ? null : new ByteRange(start, end);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String getExtension(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return (dot != -1) ? name.substring(dot + 1).toLowerCase() : "";
    }

    private static String formatHttpDate(long millis) {
        ZonedDateTime date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC);
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(date);
    }

    private String getCacheControlHeader(Path path) {
        String name = path.getFileName().toString();
        String ext = getExtension(path);
        boolean isImmutable = name.matches(".*\\.[a-f0-9]{6,}\\..*");

        if (isImmutable && immutableCandidateExtensions.contains(ext)) {
            return "public, max-age=31536000, immutable";
        }
        if ("html".equals(ext)) {
            return "public, max-age=0, must-revalidate";
        }
        return "public, max-age=" + maxAgeSeconds;
    }

    @Override
    public void close() {
        Collection<FileSystem> values = jarFileSystemCache.values();
        for (FileSystem value : values) {
            try (value) {
                values.remove(value);
            } catch (IOException ignored) {
            }
        }
    }

    protected enum Compression {
        BROTLI("br", ".br"), GZIP("gzip", ".gz"), NONE("", "");
        public final String encoding;
        public final String extension;

        Compression(String encoding, String extension) {
            this.encoding = encoding;
            this.extension = extension;
        }

        public static Compression negotiate(String acceptEncoding, Path path) {
            if (acceptEncoding == null) {
                return NONE;
            }
            if (acceptEncoding.contains("br") && Files.exists(path.resolveSibling(path.getFileName() + ".br"))) {
                return BROTLI;
            }
            if (acceptEncoding.contains("gzip") && Files.exists(path.resolveSibling(path.getFileName() + ".gz"))) {
                return GZIP;
            }
            return NONE;
        }
    }

    protected record ByteRange(long start, long end) {
        public long length() {
            return end - start + 1;
        }
    }
}
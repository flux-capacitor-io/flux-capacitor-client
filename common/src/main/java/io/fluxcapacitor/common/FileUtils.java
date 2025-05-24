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

package io.fluxcapacitor.common;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Scanner;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getCallerClass;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Utility class for reading and writing files and classpath resources.
 * <p>
 * Supports:
 * <ul>
 *   <li>Reading text files from file system, {@link URL}, {@link URI}, or classpath</li>
 *   <li>Writing string content to files</li>
 *   <li>Graceful resolution of paths in JAR and non-JAR environments</li>
 *   <li>Loading and merging {@link Properties} from multiple modules</li>
 *   <li>Safe fallbacks using {@code Optional}</li>
 * </ul>
 * <p>
 * Default character encoding is {@link StandardCharsets#UTF_8}.
 */
@Slf4j
public class FileUtils {

    /**
     * Writes a string to a file using UTF-8 encoding.
     *
     * @param fileName the target file name
     * @param content  the content to write
     */
    @SneakyThrows
    public static void writeFile(String fileName, String content) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write(content);
        }
    }

    /**
     * Loads the contents of a file located relative to the calling class.
     */
    public static String loadFile(String fileName) {
        return loadFile(getCallerClass(), fileName, UTF_8);
    }

    /**
     * Loads a classpath resource relative to a given reference class.
     */
    public static String loadFile(Class<?> referencePoint, String fileName) {
        return loadFile(referencePoint, fileName, UTF_8);
    }

    /**
     * Loads a file with a specified charset, using the calling class as reference point.
     */
    public static String loadFile(String fileName, Charset charset) {
        return loadFile(getCallerClass(), fileName, charset);
    }

    /**
     * Loads a file from a classpath location using a reference class and charset.
     *
     * @throws NullPointerException if the resource cannot be found
     */
    @SuppressWarnings("ConstantConditions")
    @SneakyThrows
    public static String loadFile(Class<?> referencePoint, String fileName, Charset charset) {
        try {
            return loadFile(referencePoint.getResource(fileName).toURI(), charset);
        } catch (NullPointerException e) {
            log.error("Resource {} not found in package {}", fileName, referencePoint.getPackageName());
            throw e;
        }
    }

    /**
     * Loads file contents from a {@link URI}.
     */
    @SneakyThrows
    public static String loadFile(URI uri) {
        return loadFile(uri, UTF_8);
    }

    /**
     * Loads file contents from a {@link URI} using a specific charset.
     */
    @SneakyThrows
    public static String loadFile(URI uri, Charset charset) {
        return loadFile(uri.toURL(), charset);
    }

    /**
     * Loads file contents from a {@link File}.
     */
    @SneakyThrows
    public static String loadFile(File file) {
        return loadFile(file, UTF_8);
    }

    /**
     * Loads file contents from a {@link File} using a specific charset.
     */
    @SneakyThrows
    public static String loadFile(File file, Charset charset) {
        return loadFile(file.toURI().toURL(), charset);
    }

    /**
     * Loads file contents from a {@link URL} using a specific charset.
     *
     * @throws IllegalArgumentException if the URL does not represent a file
     */
    @SneakyThrows
    public static String loadFile(URL url, Charset charset) {
        if (url.getFile() == null || url.getFile().isEmpty()) {
            log.error("Not a file url: {}", url);
            throw new IllegalArgumentException("Not a file url: " + url);
        }
        try (InputStream inputStream = url.openStream()) {
            return new Scanner(inputStream, charset).useDelimiter("\\A").next();
        } catch (Exception e) {
            log.error("File not found {}", url, e);
            throw e;
        }
    }

    /**
     * Attempts to load file content relative to the caller class, returns empty if not found.
     */
    public static Optional<String> tryLoadFile(String fileName) {
        return tryLoadFile(getCallerClass(), fileName, UTF_8);
    }

    /**
     * Attempts to load file content relative to a reference class.
     */
    public static Optional<String> tryLoadFile(Class<?> referencePoint, String fileName) {
        return tryLoadFile(referencePoint, fileName, UTF_8);
    }

    /**
     * Attempts to load file content with specified charset.
     */
    public static Optional<String> tryLoadFile(String fileName, Charset charset) {
        return tryLoadFile(getCallerClass(), fileName, charset);
    }

    /**
     * Attempts to load file content with specified reference class and charset.
     */
    @SuppressWarnings("ConstantConditions")
    @SneakyThrows
    public static Optional<String> tryLoadFile(Class<?> referencePoint, String fileName, Charset charset) {
        try {
            return tryLoadFile(new File(referencePoint.getResource(fileName).toURI()), charset);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /**
     * Attempts to load file content from a {@link File}.
     */
    @SneakyThrows
    public static Optional<String> tryLoadFile(File file) {
        return tryLoadFile(file, UTF_8);
    }

    /**
     * Attempts to load file content from a {@link File} with specified charset.
     */
    @SneakyThrows
    public static Optional<String> tryLoadFile(File file, Charset charset) {
        try (InputStream inputStream = new FileInputStream(file)) {
            return Optional.ofNullable(new Scanner(inputStream, charset).useDelimiter("\\A").next());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /**
     * Safely resolves a relative path against a base URI, including JAR URLs.
     */
    public static URI safeResolve(URI base, String relativePath) {
        if (base.getScheme().equals("jar")) {
            return URI.create("jar:" + URI.create(base.getSchemeSpecificPart()).resolve(relativePath));
        }
        return base.resolve(relativePath);
    }

    /**
     * Loads properties from the classpath, merging from all available matching resources.
     * <p>
     * If the same key appears in multiple modules, a warning is logged.
     *
     * @param fileName resource path to a properties file
     * @return merged properties across all modules
     */
    @SneakyThrows
    public static Properties loadProperties(String fileName) {
        fileName = fileName.startsWith("/") ? fileName.substring(1) : fileName;
        Properties result = new Properties();
        var resources = Collections.list(getCallerClass().getClassLoader().getResources(fileName)).reversed();
        for (URL resource : resources) {
            try (InputStream inputStream = resource.openStream()) {
                Properties properties = new Properties();
                properties.load(inputStream);
                properties.forEach((k, v) -> {
                    Object existing = result.put(k, v);
                    if (existing != null && !Objects.equals(existing, v)) {
                        log.warn("Property {} has been registered in more than one module. "
                                 + "This may give unpredictable results.", k);
                    }
                });
            }
        }
        return result;
    }
}

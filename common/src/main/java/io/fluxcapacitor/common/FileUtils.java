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
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Scanner;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getCallerClass;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class FileUtils {

    @SneakyThrows
    public static void writeFile(String fileName, String content) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
            writer.write(content);
        }
    }

    public static String loadFile(String fileName) {
        return loadFile(getCallerClass(), fileName, UTF_8);
    }

    public static String loadFile(Class<?> referencePoint, String fileName) {
        return loadFile(referencePoint, fileName, UTF_8);
    }

    public static String loadFile(String fileName, Charset charset) {
        return loadFile(getCallerClass(), fileName, charset);
    }

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

    @SneakyThrows
    public static String loadFile(URI uri) {
        return loadFile(uri, UTF_8);
    }

    @SneakyThrows
    public static String loadFile(URI uri, Charset charset) {
        return loadFile(new File(uri), charset);
    }

    public static String loadFile(File file) {
        return loadFile(file, UTF_8);
    }

    @SneakyThrows
    public static String loadFile(File file, Charset charset) {
        try (InputStream inputStream = new FileInputStream(file)) {
            return new Scanner(inputStream, charset).useDelimiter("\\A").next();
        } catch (Exception e) {
            log.error("File not found {}", file, e);
            throw e;
        }
    }

    public static Optional<String> tryLoadFile(String fileName) {
        return tryLoadFile(getCallerClass(), fileName, UTF_8);
    }

    public static Optional<String> tryLoadFile(Class<?> referencePoint, String fileName) {
        return tryLoadFile(referencePoint, fileName, UTF_8);
    }

    public static Optional<String> tryLoadFile(String fileName, Charset charset) {
        return tryLoadFile(getCallerClass(), fileName, charset);
    }

    @SuppressWarnings("ConstantConditions")
    @SneakyThrows
    public static Optional<String> tryLoadFile(Class<?> referencePoint, String fileName, Charset charset) {
        try {
            return tryLoadFile(new File(referencePoint.getResource(fileName).toURI()), charset);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @SneakyThrows
    public static Optional<String> tryLoadFile(File file) {
        return tryLoadFile(file, UTF_8);
    }

    @SneakyThrows
    public static Optional<String> tryLoadFile(File file, Charset charset) {
        try (InputStream inputStream = new FileInputStream(file)) {
            return Optional.ofNullable(new Scanner(inputStream, charset).useDelimiter("\\A").next());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

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

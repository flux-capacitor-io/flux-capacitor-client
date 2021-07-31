/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

import io.fluxcapacitor.common.reflection.ReflectionUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.function.Predicate;

import static java.lang.Thread.currentThread;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;

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

    @SneakyThrows
    public static String loadFile(Class<?> referencePoint, String fileName, Charset charset) {
        try (InputStream inputStream = referencePoint.getResourceAsStream(fileName)) {
            return new Scanner(inputStream, charset.name()).useDelimiter("\\A").next();
        } catch (NullPointerException e) {
            log.error("File not found {}", fileName);
            throw e;
        }
    }

    @SneakyThrows
    public static String loadFile(File file) {
        try (InputStream inputStream = new FileInputStream(file)) {
            return new Scanner(inputStream, StandardCharsets.UTF_8.name()).useDelimiter("\\A").next();
        } catch (Exception e) {
            log.error("File not found {}", file, e);
            throw e;
        }
    }

    private static Class<?> getCallerClass() {
        return stream(currentThread().getStackTrace())
                .map(StackTraceElement::getClassName)
                .filter(callerClassFilter())
                .findFirst()
                .map(ReflectionUtils::classForName)
                .orElseThrow(() -> new IllegalStateException("Could not find caller class"));
    }

    public static Predicate<String> callerClassFilter() {
        return c -> !c.startsWith("io.fluxcapacitor");
    }
}

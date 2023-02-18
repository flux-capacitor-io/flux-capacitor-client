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
import java.util.Scanner;

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
        return loadFile(ReflectionUtils.getCallerClass(), fileName, UTF_8);
    }

    public static String loadFile(Class<?> referencePoint, String fileName) {
        return loadFile(referencePoint, fileName, UTF_8);
    }

    public static String loadFile(String fileName, Charset charset) {
        return loadFile(ReflectionUtils.getCallerClass(), fileName, charset);
    }

    @SneakyThrows
    public static String loadFile(Class<?> referencePoint, String fileName, Charset charset) {
        try (InputStream inputStream = referencePoint.getResourceAsStream(fileName)) {
            return new Scanner(inputStream, charset).useDelimiter("\\A").next();
        } catch (NullPointerException e) {
            log.error("Resource {} not found in package {}", fileName, referencePoint.getPackageName());
            throw e;
        }
    }

    @SneakyThrows
    public static String loadFile(File file) {
        try (InputStream inputStream = new FileInputStream(file)) {
            return new Scanner(inputStream, UTF_8).useDelimiter("\\A").next();
        } catch (Exception e) {
            log.error("File not found {}", file, e);
            throw e;
        }
    }

}

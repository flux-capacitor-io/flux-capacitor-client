package io.fluxcapacitor.javaclient.common;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
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
                .map(FileUtils::forName)
                .orElseThrow(() -> new IllegalStateException("Could not find caller class"));
    }

    public static Predicate<String> callerClassFilter() {
        return c -> c.startsWith("io.fluxcapacitor.javaclient") &&
                !c.startsWith("io.fluxcapacitor.javaclient.common");
    }

    @SneakyThrows
    private static Class<?> forName(String name) {
        return Class.forName(name);
    }
}

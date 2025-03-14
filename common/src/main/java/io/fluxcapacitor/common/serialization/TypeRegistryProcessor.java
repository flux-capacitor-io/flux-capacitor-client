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

package io.fluxcapacitor.common.serialization;

import com.google.auto.service.AutoService;
import lombok.SneakyThrows;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

@SupportedAnnotationTypes("io.fluxcapacitor.common.serialization.RegisterType")
@AutoService(Processor.class)
public class TypeRegistryProcessor extends AbstractProcessor {
    static final String CLASS_NAME = "GeneratedTypeRegistry";
    static final String PACKAGE_NAME = "io.fluxcapacitor.common.serialization";

    final Set<String> prefixes = new HashSet<>();
    final Set<String> classes = new TreeSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            writeClass();
            return true;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(RegisterType.class)) {
            if (element instanceof QualifiedNameable typeOrPackage) {
                prefixes.add(typeOrPackage.getQualifiedName().toString());
            }
        }
        roundEnv.getRootElements().stream()
                .filter(e -> e instanceof TypeElement t
                             && prefixes.stream().anyMatch(root -> t.getQualifiedName().toString().startsWith(root)))
                .flatMap(e -> getClasses((TypeElement) e))
                .forEach(c -> classes.add(processingEnv.getElementUtils().getBinaryName(c).toString()));
        return true;
    }

    Stream<TypeElement> getClasses(TypeElement type) {
        return Stream.concat(Stream.of(type), type.getEnclosedElements().stream()
                .filter(e -> e instanceof TypeElement).flatMap(e -> getClasses((TypeElement) e)));
    }

    @SneakyThrows
    void writeClass() {
        Set<String> existingClasses = new TreeSet<>(classes);

        try {
            FileObject resource = processingEnv.getFiler().getResource(
                    StandardLocation.CLASS_OUTPUT, "io.fluxcapacitor.common.serialization",
                    "generated-type-registry");
            try (Scanner scanner = new Scanner(resource.openInputStream())) {
                while (scanner.hasNextLine()) {
                    existingClasses.add(scanner.nextLine());
                }
            }
        } catch (IOException ignored) {
        }

        FileObject resource = processingEnv.getFiler().createResource(
                StandardLocation.CLASS_OUTPUT, "io.fluxcapacitor.common.serialization", "generated-type-registry");
        try (Writer resourceWriter = resource.openWriter()) {
            for (String type : existingClasses) {
                resourceWriter.write(type + "\n");
            }
        }

        StringBuilder registryCode = new StringBuilder()
                .append("package ").append(PACKAGE_NAME).append(";\n\n")
                .append("public class GeneratedTypeRegistry extends TypeRegistry {\n\n")
                .append(" public static TypeRegistry INSTANCE = new GeneratedTypeRegistry();\n\n")
                .append(" private GeneratedTypeRegistry() {\n")
                .append("    super(getElements());\n")
                .append(" }\n\n")
                .append("    private static java.util.List<java.lang.String> getElements() {\n")
                .append("        java.util.List<java.lang.String> result = new java.util.ArrayList<>();\n");
        for (String type : existingClasses) {
            registryCode.append("        result.add(\"").append(type).append("\");\n");
        }
        registryCode
                .append("        return result;\n")
                .append("    }\n\n")
                .append("}\n");

        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(PACKAGE_NAME + "." + CLASS_NAME);
            try (Writer writer = file.openWriter()) {
                writer.write(registryCode.toString());
            }
//            processingEnv.getMessager().printNote("✅ Successfully generated " + CLASS_NAME);
        }  catch (FilerException ignored) {
        }
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }
}

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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Value;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.call;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Stream.concat;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

@SupportedAnnotationTypes(TypeRegistryProcessor.ANNOTATION)
@AutoService(Processor.class)
public class TypeRegistryProcessor extends AbstractProcessor {
    static final String ANNOTATION = "io.fluxcapacitor.common.serialization.RegisterType";
    public static final String TYPES_FILE = "META-INF/" + TypeRegistry.class.getName();
    private static final String PREFIXES_FILE = "META-INF/type-registry-prefixes";

    private final Set<Prefix> roundPrefixes = new LinkedHashSet<>();
    @Getter(lazy = true)
    private final FileObject typesResource =
            call(() -> processingEnv.getFiler().getResource(CLASS_OUTPUT, "", TYPES_FILE));
    @Getter(lazy = true)
    private final FileObject prefixesResource =
            call(() -> processingEnv.getFiler().getResource(CLASS_OUTPUT, "", PREFIXES_FILE));

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        if (!isNewProcess()) {
            storeTypes();
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        roundPrefixes.addAll(roundEnv.getRootElements().stream().flatMap(this::getPrefixes).toList());
        if (roundEnv.processingOver() && isNewProcess()) {
            storeTypes();
        }
        return true;
    }

    boolean isNewProcess() {
        return getTypesResource().getLastModified() == 0;
    }

    @SneakyThrows
    void storeTypes() {
        var prefixes = updateAndGetPrefixes();
        var types = Stream.concat(getStoredTypes(prefixes), getNewTypes(prefixes)).collect(toCollection(TreeSet::new));
        try (var resourceWriter = processingEnv.getFiler().createResource(CLASS_OUTPUT, "", TYPES_FILE)
                .openWriter()) {
            for (String type : types) {
                resourceWriter.write(type + "\n");
            }
        }
    }

    @SneakyThrows
    Stream<String> getStoredTypes(Set<Prefix> prefixes) {
        if (isNewProcess()) {
            return Stream.empty();
        }
        Collection<String> result = new ArrayList<>();
        try (Scanner scanner = new Scanner(getTypesResource().openInputStream())) {
            while (scanner.hasNextLine()) {
                String type = scanner.nextLine();
                if (isType(type) && prefixes.stream().anyMatch(p -> p.matches(type))) {
                    result.add(type);
                }
            }
        }
        return result.stream();
    }

    Stream<String> getNewTypes(Set<Prefix> prefixes) {
        return processingEnv.getElementUtils().getAllModuleElements().stream().flatMap(this::getClasses)
                .filter(t -> prefixes.stream().anyMatch(prefix -> prefix.matches(t.getQualifiedName().toString())))
                .map(c -> processingEnv.getElementUtils().getBinaryName(c).toString());
    }

    @SneakyThrows
    Set<Prefix> updateAndGetPrefixes() {
        Set<Prefix> prefixes = new LinkedHashSet<>(roundPrefixes);
        FileObject resource = getPrefixesResource();
        if (resource.getLastModified() != 0) {
            try (Scanner scanner = new Scanner(resource.openInputStream())) {
                while (scanner.hasNextLine()) {
                    Prefix prefix = new Prefix(scanner.nextLine());
                    String root = prefix.getRoot();
                    if (isPackage(root) || isType(root)) {
                        prefixes.add(prefix);
                    }
                }
            }
        }
        resource = processingEnv.getFiler().createResource(CLASS_OUTPUT, "", PREFIXES_FILE);
        try (Writer resourceWriter = resource.openWriter()) {
            for (Prefix prefix : prefixes) {
                String root = prefix.getRoot();
                if (isPackage(root) || isType(root)) {
                    resourceWriter.write(prefix + "\n");
                }
            }
        }
        return prefixes;
    }

    Stream<Prefix> getPrefixes(Element element) {
        try {
            RegisterType registerType = element.getAnnotation(RegisterType.class);
            if (registerType != null) {
                String root = ((QualifiedNameable) element).getQualifiedName().toString();
                return Stream.of(new Prefix(root, Arrays.asList(registerType.contains())));
            }
        } catch (Throwable ignored) {
        }
        return element.getEnclosedElements().stream().flatMap(this::getPrefixes);
    }

    Stream<TypeElement> getClasses(Element element) {
        try {
            return concat(element instanceof TypeElement t ? Stream.of(t) : Stream.empty(),
                          element.getEnclosedElements().stream().flatMap(this::getClasses));
        } catch (Throwable e) {
            processingEnv.getMessager()
                    .printWarning("Failed to get classes of element: " + element + ". " + e.getMessage());
            return Stream.empty();
        }
    }

    boolean isType(String fqn) {
        return processingEnv.getElementUtils().getTypeElement(fqn.replace("$", ".")) != null;
    }

    boolean isPackage(String fqn) {
        return processingEnv.getElementUtils().getPackageElement(fqn) != null;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Value
    @AllArgsConstructor
    static class Prefix {
        String root;
        List<String> filters;

        @Getter(lazy = true)
        Predicate<String> filterMatch = getFilters().stream().map(Pattern::compile)
                .map(Pattern::asPredicate).reduce(Predicate::or).orElse(s -> true);

        public Prefix(String storedPrefix) {
            var parts = storedPrefix.split(",");
            root = parts[0];
            filters = Arrays.stream(parts).skip(1).toList();
        }

        public boolean matches(String type) {
            type = type.replace("$", ".");
            return type.startsWith(root) && getFilterMatch().test(type);
        }

        @Override
        public String toString() {
            return Stream.concat(Stream.of(root), filters.stream()).collect(Collectors.joining(","));
        }
    }
}

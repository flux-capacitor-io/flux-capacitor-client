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

import com.google.auto.service.AutoService;
import io.fluxcapacitor.common.reflection.ParameterRegistry;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileObject;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SupportedAnnotationTypes({
        "io.fluxcapacitor.javaclient.web.QueryParam",
        "io.fluxcapacitor.javaclient.web.PathParam",
        "io.fluxcapacitor.javaclient.web.CookieParam",
        "io.fluxcapacitor.javaclient.web.HeaderParam",
        "io.fluxcapacitor.javaclient.web.FormParam"})
@AutoService(Processor.class)
public class WebParameterProcessor extends AbstractProcessor {

    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Map<TypeElement, List<ExecutableElement>> methodsMap = new HashMap<>();
        for (Element element : roundEnv.getElementsAnnotatedWithAny(Set.of(QueryParam.class, PathParam.class, CookieParam.class, HeaderParam.class, FormParam.class))) {
            if (element.getEnclosingElement() instanceof ExecutableElement method) {
                methodsMap.computeIfAbsent((TypeElement) method.getEnclosingElement(), c -> new ArrayList<>())
                        .add(method);
            }
        }

        if (!methodsMap.isEmpty()) {
            methodsMap.forEach(this::generateParamsClass);
        }

        return true;
    }

    private String generateMethodSignature(ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        String parameterTypes = method.getParameters()
                .stream()
                .map(param -> param.asType().toString()) // Fully qualified parameter types
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return methodName + "(" + parameterTypes + ")";
    }

    private void generateParamsClass(TypeElement type, List<ExecutableElement> methods) {
        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(type);
        String packageName = packageElement.getQualifiedName().toString();
        String simpleClassName = type.getQualifiedName().toString().replace(packageName + ".", "").replace(".", "_") + "_params";
        String fullClassName = packageName + "." + simpleClassName;

        StringBuilder content = new StringBuilder();
        content.append("package ").append(packageName).append(";\n\n");
        content.append("import java.lang.reflect.Method;\n");
        content.append("import java.util.HashMap;\n");
        content.append("import java.util.Map;\n\n");
        content.append("import java.util.List;\n\n");
        content.append("public class ").append(simpleClassName).append(" extends ")
                .append(ParameterRegistry.class.getName())
                .append(" {\n");
        content.append("\tpublic ").append(simpleClassName).append("() {\n");
        content.append("\t\tsuper(methodParameters());\n");
        content.append("\t}\n\n");

        content.append("\tstatic Map<String, List<String>> methodParameters() {\n");
        content.append("\t\tMap<String, List<String>> result = new HashMap<>();\n");
        methods.forEach(m -> {
            content.append("\t\tresult.put(\"").append(ParameterRegistry.signature(m)).append("\", List.of(")
                    .append(String.join(", ", m.getParameters().stream().map(VariableElement::getSimpleName).map(Name::toString).map(name -> "\"" + name + "\"").toList()))
                    .append("));\n");
        });
        content.append("\t\treturn result;\n");
        content.append("\t}\n");

        content.append("}\n");

        try {
            JavaFileObject file = filer.createSourceFile(fullClassName);
            try (Writer writer = file.openWriter()) {
                writer.write(content.toString());
            }
        } catch (Exception e) {
            messager.printError("Error generating DumpedParams: " + e.getMessage());
        }

    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }
}

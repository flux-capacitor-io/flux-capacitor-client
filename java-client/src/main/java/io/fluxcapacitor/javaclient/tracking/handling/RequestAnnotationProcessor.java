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

package io.fluxcapacitor.javaclient.tracking.handling;

import com.google.auto.service.AutoService;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

@SupportedAnnotationTypes({
        "io.fluxcapacitor.javaclient.tracking.handling.HandleQuery",
        "io.fluxcapacitor.javaclient.tracking.handling.HandleCommand"})
@AutoService(Processor.class)
public class RequestAnnotationProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        TypeMirror requestType = getTypeUtils().erasure(getElementUtils().getTypeElement(
                "io.fluxcapacitor.javaclient.tracking.handling.Request").asType());
        for (TypeElement annotation : annotations) {
            for (Element method : roundEnv.getElementsAnnotatedWith(annotation)) {
                validateMethod(method, requestType);
            }
        }
        return false;
    }

    protected void validateMethod(Element method, TypeMirror requestType) {
        if (isPassive(method)) {
            return;
        }
        var classType = method.getEnclosingElement().asType();
        if (getTypeUtils().isAssignable(classType, requestType)) {
            //request handles itself
            validateReturnType(method, classType, requestType);
        } else {
            for (TypeMirror p : ((ExecutableType) method.asType()).getParameterTypes()) {
                validateReturnType(method, p, requestType);
            }
        }
    }

    protected void validateReturnType(Element method, TypeMirror payloadType, TypeMirror requestType) {
        if (!getTypeUtils().isAssignable(payloadType, requestType)) {
            return;
        }
        ExecutableType methodType = (ExecutableType) method.asType();
        for (TypeMirror i : ((TypeElement) (((DeclaredType) payloadType).asElement())).getInterfaces()) {
            if (getTypeUtils().isAssignable(payloadType, requestType)) {
                List<? extends TypeMirror> typeArguments = ((DeclaredType) i).getTypeArguments();
                if (!typeArguments.isEmpty()) {
                    TypeMirror futureTypeElement = processingEnv.getElementUtils()
                            .getTypeElement(Future.class.getCanonicalName()).asType();

                    TypeMirror expectedReturnType = typeArguments.getFirst();
                    TypeMirror handlerReturnType = methodType.getReturnType();
                    if (getTypeUtils().isAssignable(getTypeUtils().erasure(handlerReturnType), getTypeUtils().erasure(futureTypeElement))) {
                        List<? extends TypeMirror> futureTypeArgs = ((DeclaredType) handlerReturnType).getTypeArguments();
                        if (futureTypeArgs.isEmpty()) {
                            processingEnv.getMessager().printMessage(
                                    Diagnostic.Kind.ERROR,
                                    "Return type of request handler is invalid. Should be assignable to Future<%s>"
                                            .formatted(expectedReturnType),
                                    method);
                            return;
                        }
                        handlerReturnType = futureTypeArgs.getFirst();
                    }
                    if (!getTypeUtils().isAssignable(handlerReturnType, expectedReturnType)) {
                        processingEnv.getMessager().printMessage(
                                Diagnostic.Kind.ERROR,
                                "Return type of request handler is invalid. Should be " + expectedReturnType,
                                method);
                        return;
                    }
                }
            }
        }
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean isPassive(Element method) {
        if (Optional.ofNullable(method.getAnnotation(HandleCommand.class))
                .map(HandleCommand::passive).orElse(false)) {
            return true;
        }
        if (Optional.ofNullable(method.getAnnotation(HandleQuery.class))
                .map(HandleQuery::passive).orElse(false)) {
            return true;
        }
        return false;
    }

    private Types getTypeUtils() {
        return processingEnv.getTypeUtils();
    }

    private Elements getElementUtils() {
        return processingEnv.getElementUtils();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }
}

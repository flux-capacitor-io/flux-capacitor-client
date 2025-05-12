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
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        if (findSuperType(payloadType, requestType) instanceof DeclaredType declaredRequest) {
            List<? extends TypeMirror> typeArguments = declaredRequest.getTypeArguments();
            if (typeArguments.isEmpty()) {
                return;
            }

            TypeMirror expectedReturnType = typeArguments.getFirst();
            TypeMirror handlerReturnType = ((ExecutableType) method.asType()).getReturnType();

            TypeMirror futureType = getElementUtils().getTypeElement(Future.class.getCanonicalName()).asType();
            if (getTypeUtils().isAssignable(getTypeUtils().erasure(handlerReturnType),
                                            getTypeUtils().erasure(futureType))) {
                List<? extends TypeMirror> futureTypeArgs = ((DeclaredType) handlerReturnType).getTypeArguments();
                if (futureTypeArgs.isEmpty()) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "Return type of request handler is invalid. Should be assignable to Future<"
                            + expectedReturnType + ">",
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
            }
        }
    }

    private TypeMirror findSuperType(TypeMirror type, TypeMirror target) {
        return findSuperType(type, target, new HashMap<>());
    }

    private TypeMirror findSuperType(TypeMirror type, TypeMirror target, Map<TypeVariable, TypeMirror> typeVarMap) {
        Types types = getTypeUtils();

        if (type == null || type.getKind() != TypeKind.DECLARED) {
            return null;
        }

        DeclaredType declaredType = (DeclaredType) type;
        TypeElement element = (TypeElement) declaredType.asElement();

        if (types.isSameType(types.erasure(type), types.erasure(target))) {
            return reifyType(declaredType, typeVarMap);
        }

        // Build map of type vars in this level
        List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
        List<? extends TypeParameterElement> typeParams = element.getTypeParameters();
        Map<TypeVariable, TypeMirror> newMap = new HashMap<>(typeVarMap);
        for (int i = 0; i < Math.min(typeParams.size(), typeArgs.size()); i++) {
            TypeVariable variable = (TypeVariable) typeParams.get(i).asType();
            newMap.put(variable, resolveTypeVar(typeArgs.get(i), typeVarMap));
        }

        // Check interfaces
        for (TypeMirror iface : element.getInterfaces()) {
            TypeMirror resolved = findSuperType(iface, target, newMap);
            if (resolved != null) {
                return resolved;
            }
        }

        // Check superclass
        TypeMirror superclass = element.getSuperclass();
        if (superclass.getKind() != TypeKind.NONE) {
            TypeMirror resolved = findSuperType(superclass, target, newMap);
            if (resolved != null) {
                return resolved;
            }
        }

        return null;
    }

    private TypeMirror resolveTypeVar(TypeMirror type, Map<TypeVariable, TypeMirror> map) {
        if (type instanceof TypeVariable var) {
            return map.getOrDefault(var, getObjectType());
        } else {
            return type;
        }
    }

    private DeclaredType reifyType(DeclaredType type, Map<TypeVariable, TypeMirror> map) {
        List<? extends TypeMirror> args = type.getTypeArguments();
        List<TypeMirror> resolvedArgs = new ArrayList<>();
        for (TypeMirror arg : args) {
            resolvedArgs.add(resolveTypeVar(arg, map));
        }
        return getTypeUtils().getDeclaredType((TypeElement) type.asElement(), resolvedArgs.toArray(new TypeMirror[0]));
    }

    private TypeMirror getObjectType() {
        return getElementUtils().getTypeElement("java.lang.Object").asType();
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

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

@SupportedAnnotationTypes({"io.fluxcapacitor.javaclient.tracking.handling.HandleQuery", "io.fluxcapacitor.javaclient.tracking.handling.HandleCommand"})
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
        ExecutableType methodType = (ExecutableType) method.asType();
        if (isPassive(method)) {
            return;
        }
        for (TypeMirror p : methodType.getParameterTypes()) {
            if (getTypeUtils().isAssignable(p, requestType)) {
                var interfaces = ((TypeElement) (((DeclaredType) p).asElement())).getInterfaces();
                for (TypeMirror i : interfaces) {
                    if (getTypeUtils().isAssignable(p, requestType)) {
                        List<? extends TypeMirror> typeArguments = ((DeclaredType) i).getTypeArguments();
                        if (!typeArguments.isEmpty()) {
                            TypeMirror expectedReturnType = typeArguments.get(0);
                            if (!getTypeUtils().isAssignable(methodType.getReturnType(), expectedReturnType)) {
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

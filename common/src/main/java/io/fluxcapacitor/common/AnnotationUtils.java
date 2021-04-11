package io.fluxcapacitor.common;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

/**
 * Helper methods to support inheritance for annotations. Only for direct parent-child relationships, not multiple levels of inheritance.
 */

public class AnnotationUtils {

    public static boolean isAssignableFrom(Class<? extends Annotation> genericAnnotation, Class<? extends Annotation> specificAnnotation) {
        return genericAnnotation.equals(specificAnnotation) || specificAnnotation.isAnnotationPresent(genericAnnotation);
    }

    public static boolean isAnnotationPresent(Executable executable, Class<? extends Annotation> genericAnnotation) {
        return executable.isAnnotationPresent(genericAnnotation) || Arrays.stream(executable.getAnnotations())
                .anyMatch(a -> isAssignableFrom(genericAnnotation, a.annotationType()));
    }

    public static Annotation getSpecificAnnotation(Executable executable, Class<? extends Annotation> genericAnnotation) {
        return executable.isAnnotationPresent(genericAnnotation) ? executable.getAnnotation(genericAnnotation) :
                Arrays.stream(executable.getAnnotations())
                        .filter(a -> isAssignableFrom(genericAnnotation, a.annotationType()))
                        .findFirst().orElse(null);
    }

    public static Class<? extends Annotation> getSpecificAnnotationType(Executable executable, Class<? extends Annotation> genericAnnotation) {
        return executable.isAnnotationPresent(genericAnnotation) ? genericAnnotation :
                Arrays.stream(executable.getAnnotations())
                        .<Class<? extends Annotation>>map(Annotation::annotationType)
                        .filter(a -> isAssignableFrom(genericAnnotation, a))
                        .findFirst().orElse(genericAnnotation);
    }


    public static Object invokeAnnotationMethod(Annotation annotation, String methodName) {
        Optional<Method> method = Arrays.stream(annotation.annotationType().getMethods())
                .filter(m -> m.getName().equals(methodName)).findFirst();
        try {
            return method.isPresent() ? method.get().invoke(annotation) : null;
        } catch (Exception e) {
            return null;
        }
    }

}

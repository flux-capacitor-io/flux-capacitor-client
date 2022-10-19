package io.fluxcapacitor.common.reflection;

import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.jvm.ReflectJvmMapping;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class KotlinReflectionUtils {

    public static KParameter asKotlinParameter(Parameter parameter) {
        var executable = parameter.getDeclaringExecutable();
        var paramIndex = ReflectionUtils.getParameterIndex(parameter);
        KFunction<?> kotlinFunction = asKotlinFunction(executable);
        if (kotlinFunction == null) {
            throw new IllegalStateException("Could not obtain Kotlin function for: " + executable);
        }
        return kotlinFunction.getParameters().stream().filter(p -> p.getKind() == KParameter.Kind.VALUE)
                .skip(paramIndex).findFirst().orElse(null);
    }

    public static KFunction<?> asKotlinFunction(Executable executable) {
        return executable instanceof Method
                ? ReflectJvmMapping.getKotlinFunction((Method) executable)
                : ReflectJvmMapping.getKotlinFunction((Constructor<?>) executable);
    }

}

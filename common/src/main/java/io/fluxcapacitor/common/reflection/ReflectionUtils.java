/*
 * Copyright (c) 2016-2018 Flux Capacitor.
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

package io.fluxcapacitor.common.reflection;

import io.fluxcapacitor.common.ObjectUtils;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static java.security.AccessController.doPrivileged;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;

public class ReflectionUtils {

    public static Stream<Method> getAllMethods(Class type) {
        return ObjectUtils.iterate(type, Class::getSuperclass, Objects::isNull).filter(Objects::nonNull)
                .flatMap(c -> stream(c.getDeclaredMethods()));
    }

    public static Optional<?> getAnnotatedPropertyValue(Object target, Class<? extends Annotation> annotation) {
        return getAnnotatedProperties(target, annotation).stream().findFirst().map(m -> getProperty(m, target));
    }
    
    public static List<? extends AccessibleObject> getAnnotatedProperties(Object target, Class<? extends Annotation> annotation) {
        if (target == null) {
            return emptyList();
        }
        List<AccessibleObject> result = new ArrayList<>(FieldUtils.getFieldsListWithAnnotation(target.getClass(), annotation));
        result.addAll(MethodUtils.getMethodsListWithAnnotation(target.getClass(), annotation, true, true));
        ClassUtils.getAllInterfaces(target.getClass()).forEach(i -> result.addAll(FieldUtils.getFieldsListWithAnnotation(i, annotation)));
        return result;
    }

    public static List<Field> getAnnotatedFields(Object target, Class<? extends Annotation> annotation) {
        if (target == null) {
            return emptyList();
        }
        return new ArrayList<>(FieldUtils.getFieldsListWithAnnotation(target.getClass(), annotation));
    }

    @SneakyThrows
    public static Object getProperty(AccessibleObject fieldOrMethod, Object target) {
        ensureAccessible(fieldOrMethod);
        if (fieldOrMethod instanceof Method) {
            return ((Method) fieldOrMethod).invoke(target);
        }
        if (fieldOrMethod instanceof Field) {
            return ((Field) fieldOrMethod).get(target);
        }
        throw new IllegalStateException("Object property should be field or method: " + fieldOrMethod);
    }
    
    @SneakyThrows
    public static void setField(Field field, Object target, Object value) {
        ensureAccessible(field).set(target, value);
    }

    @SneakyThrows
    public static void setField(String fieldName, Object target, Object value) {
        setField(target.getClass().getDeclaredField(fieldName), target, value);
    }

    public static <T extends AccessibleObject> T ensureAccessible(T member) {
        doPrivileged((PrivilegedAction<?>) () -> {
            member.setAccessible(true);
            return null;
        });
        return member;
    }

}

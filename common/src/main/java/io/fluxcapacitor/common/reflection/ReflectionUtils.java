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

import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.security.AccessController.doPrivileged;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.ClassUtils.getAllInterfaces;
import static org.apache.commons.lang3.StringUtils.isEmpty;

public class ReflectionUtils {

    public static Stream<Method> getAllMethods(Class type) {
        return getAllMethods(type, true, false).stream();
    }
    
    /*
        Adopted from https://stackoverflow.com/questions/28400408/what-is-the-new-way-of-getting-all-methods-of-a-class-including-inherited-defau
     */
    public static Collection<Method> getAllMethods(Class clazz,
                                                   boolean includePrivateMethodsOfSuperclasses,
                                                   boolean includeOverriddenAndHidden) {

        Predicate<Method> include = m -> !m.isBridge() && !m.isSynthetic() &&
                Character.isJavaIdentifierStart(m.getName().charAt(0))
                && m.getName().chars().skip(1).allMatch(Character::isJavaIdentifierPart);

        Set<Method> methods = new LinkedHashSet<>();
        Collections.addAll(methods, clazz.getMethods());
        methods.removeIf(include.negate());
        Stream.of(clazz.getDeclaredMethods()).filter(include).forEach(methods::add);

        final int access = Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE;

        Package p = clazz.getPackage();
        if (!includePrivateMethodsOfSuperclasses) {
            int pass = includeOverriddenAndHidden ?
                    Modifier.PUBLIC | Modifier.PROTECTED : Modifier.PROTECTED;
            include = include.and(m -> {
                int mod = m.getModifiers();
                return (mod & pass) != 0
                        || (mod & access) == 0 && m.getDeclaringClass().getPackage() == p;
            });
        }
        if (!includeOverriddenAndHidden) {
            Map<Object, Set<Package>> types = new HashMap<>();
            final Set<Package> pkgIndependent = Collections.emptySet();
            for (Method m : methods) {
                int acc = m.getModifiers() & access;
                if (acc == Modifier.PRIVATE) {
                    continue;
                }
                if (acc != 0) {
                    types.put(methodKey(m), pkgIndependent);
                } else {
                    types.computeIfAbsent(methodKey(m), x -> new HashSet<>()).add(p);
                }
            }
            include = include.and(m -> {
                int acc = m.getModifiers() & access;
                return acc != 0 ? acc == Modifier.PRIVATE
                        || types.putIfAbsent(methodKey(m), pkgIndependent) == null :
                        noPkgOverride(m, types, pkgIndependent);
            });
        }
        for (clazz = clazz.getSuperclass(); clazz != null; clazz = clazz.getSuperclass()) {
            Stream.of(clazz.getDeclaredMethods()).filter(include).forEach(methods::add);
        }
        return methods;
    }

    private static boolean noPkgOverride(
            Method m, Map<Object, Set<Package>> types, Set<Package> pkgIndependent) {
        Set<Package> pkg = types.computeIfAbsent(methodKey(m), key -> new HashSet<>());
        return pkg != pkgIndependent && pkg.add(m.getDeclaringClass().getPackage());
    }

    private static Object methodKey(Method m) {
        return Arrays.asList(m.getName(),
                             MethodType.methodType(m.getReturnType(), m.getParameterTypes()));
    }


    public static Optional<?> getAnnotatedPropertyValue(Object target, Class<? extends Annotation> annotation) {
        return getAnnotatedProperties(target, annotation).stream().findFirst().map(m -> getProperty(m, target));
    }

    public static List<? extends AccessibleObject> getAnnotatedProperties(Object target,
                                                                          Class<? extends Annotation> annotation) {
        if (target == null) {
            return emptyList();
        }
        List<AccessibleObject> result =
                new ArrayList<>(FieldUtils.getFieldsListWithAnnotation(target.getClass(), annotation));
        result.addAll(MethodUtils.getMethodsListWithAnnotation(target.getClass(), annotation, true, true));
        getAllInterfaces(target.getClass())
                .forEach(i -> result.addAll(FieldUtils.getFieldsListWithAnnotation(i, annotation)));
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
    
    public static Class<?> getCollectionElementType(Type parameterizedType) {
        if (parameterizedType instanceof ParameterizedType) {
            Type elementType = ((ParameterizedType) parameterizedType).getActualTypeArguments()[0];
            if (elementType instanceof WildcardType) {
                Type[] upperBounds = ((WildcardType) elementType).getUpperBounds();
                elementType = upperBounds.length > 0 ? upperBounds[0] : null;
            }
            return elementType instanceof Class<?> ? (Class<?>) elementType : Object.class;
        }
        return Object.class;
    }

    public static boolean declaresField(Class<?> target, String fieldName) {
        return !isEmpty(fieldName) && FieldUtils.getDeclaredField(target, fieldName, true) != null;
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

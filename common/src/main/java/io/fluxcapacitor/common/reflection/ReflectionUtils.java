/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Executable;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static java.beans.Introspector.getBeanInfo;
import static java.lang.Integer.compare;
import static java.security.AccessController.doPrivileged;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.ClassUtils.getAllInterfaces;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.reflect.MethodUtils.getMethodsListWithAnnotation;

public class ReflectionUtils {
    private static final int ACCESS_MODIFIERS = Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE;
    private static final List<Integer> ACCESS_ORDER = List.of(Modifier.PRIVATE, 0, Modifier.PROTECTED, Modifier.PUBLIC);

    private static final Function<Class<?>, List<Method>> methodsCache = memoize(ReflectionUtils::computeAllMethods);
    private static final Function<String, Class<?>> classForNameCache = memoize(ReflectionUtils::computeClass);
    private static final BiFunction<String, Class<?>, Function<Object, Object>> gettersCache =
            memoize(ReflectionUtils::computeNestedGetter);
    private static final BiFunction<String, Class<?>, BiConsumer<Object, Object>> settersCache =
            memoize(ReflectionUtils::computeNestedSetter);

    public static List<Method> getAllMethods(Class<?> type) {
        return methodsCache.apply(type);
    }

    /*
       Adopted from https://stackoverflow.com/questions/28400408/what-is-the-new-way-of-getting-all-methods-of-a-class-including-inherited-defau
    */
    private static List<Method> computeAllMethods(Class<?> type) {
        Predicate<Method> include = m -> !m.isBridge() && !m.isSynthetic() &&
                Character.isJavaIdentifierStart(m.getName().charAt(0))
                && m.getName().chars().skip(1).allMatch(Character::isJavaIdentifierPart);

        Set<Method> methods = new LinkedHashSet<>();
        Collections.addAll(methods, type.getMethods());
        methods.removeIf(include.negate());
        Stream.of(type.getDeclaredMethods()).filter(include).forEach(methods::add);

        final int access = Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE;

        Package p = type.getPackage();
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
        for (type = type.getSuperclass(); type != null; type = type.getSuperclass()) {
            Stream.of(type.getDeclaredMethods()).filter(include).forEach(methods::add);
        }
        return new ArrayList<>(methods);
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
        return getAnnotatedProperties(target, annotation).stream().findFirst().map(m -> getValue(m, target));
    }

    public static List<? extends AccessibleObject> getAnnotatedProperties(Object target,
                                                                          Class<? extends Annotation> annotation) {
        if (target == null) {
            return emptyList();
        }
        return getAnnotatedProperties(target.getClass(), annotation);
    }

    public static List<? extends AccessibleObject> getAnnotatedProperties(Class<?> target,
                                                                          Class<? extends Annotation> annotation) {
        List<AccessibleObject> result =
                new ArrayList<>(FieldUtils.getFieldsListWithAnnotation(target, annotation));
        result.addAll(getMethodsListWithAnnotation(target, annotation, true, true).stream()
                              .filter(m -> m.getParameterCount() == 0).collect(toList()));
        getAllInterfaces(target)
                .forEach(i -> result.addAll(FieldUtils.getFieldsListWithAnnotation(i, annotation)));
        return result;
    }

    public static List<Method> getAnnotatedMethods(Object target, Class<? extends Annotation> annotation) {
        return getMethodsListWithAnnotation(target.getClass(), annotation, true, true);
    }

    public static List<Field> getAnnotatedFields(Object target, Class<? extends Annotation> annotation) {
        if (target == null) {
            return emptyList();
        }
        return new ArrayList<>(FieldUtils.getFieldsListWithAnnotation(target.getClass(), annotation));
    }

    public static boolean isAnnotationPresent(Class<?> type, Class<? extends Annotation> annotationType) {
        return getTypeAnnotation(type, annotationType) != null;
    }

    public static <A extends Annotation> A getTypeAnnotation(Class<?> type, Class<A> annotationType) {
        A result = type.getAnnotation(annotationType);
        if (result == null) {
            for (Class<?> iFace : type.getInterfaces()) {
                result = iFace.getAnnotation(annotationType);
                if (result != null) {
                    break;
                }
            }
        }
        return result;
    }

    /*
        Read a property
     */

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> readProperty(String propertyPath, Object target) {
        if (target == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(gettersCache.apply(propertyPath, target.getClass()).apply(target))
                    .map(v -> (T) v);
        } catch (PropertyNotFoundException ignored) {
            return Optional.empty();
        }
    }

    public static boolean hasProperty(String propertyPath, Object target) {
        if (target == null) {
            return false;
        }
        try {
            gettersCache.apply(propertyPath, target.getClass()).apply(target);
            return true;
        } catch (PropertyNotFoundException ignored) {
            return false;
        }
    }

    private static Function<Object, Object> computeNestedGetter(String propertyPath, Class<?> type) {
        String[] parts = Arrays.stream(propertyPath.replace('.', '/').split("/"))
                .filter(s -> !s.isBlank()).toArray(String[]::new);
        if (parts.length == 1) {
            return computeGetter(parts[0], type);
        }
        return object -> {
            for (String part : parts) {
                if (object == null) {
                    return null;
                }
                object = gettersCache.apply(part, object.getClass()).apply(object);
            }
            return object;
        };
    }

    @SneakyThrows
    private static Function<Object, Object> computeGetter(@NonNull String propertyName, @NonNull Class<?> type) {
        return Arrays.stream(getBeanInfo(type, Object.class).getPropertyDescriptors())
                .filter(d -> propertyName.equals(d.getName()))
                .<AccessibleObject>map(PropertyDescriptor::getReadMethod).filter(Objects::nonNull).findFirst()
                .or(() -> Optional.ofNullable(MethodUtils.getMatchingMethod(type, propertyName)))
                .or(() -> Optional.ofNullable(FieldUtils.getField(type, propertyName, true)))
                .<Function<Object, Object>>map(a -> target -> getValue(a, target))
                .orElseThrow(() -> new PropertyNotFoundException(propertyName, type));
    }

    @SneakyThrows
    public static Object getValue(AccessibleObject fieldOrMethod, Object target) {
        ensureAccessible(fieldOrMethod);
        if (fieldOrMethod instanceof Method) {
            return ((Method) fieldOrMethod).invoke(target);
        }
        if (fieldOrMethod instanceof Field) {
            return ((Field) fieldOrMethod).get(target);
        }
        throw new IllegalStateException("Object property should be field or method: " + fieldOrMethod);
    }

    /*
        Write a property
     */

    public static void writeProperty(String propertyPath, Object target, Object value) {
        if (target != null) {
            try {
                settersCache.apply(propertyPath, target.getClass()).accept(target, value);
            } catch (PropertyNotFoundException ignored) {
            }
        }
    }

    @SneakyThrows
    private static BiConsumer<Object, Object> computeNestedSetter(@NonNull String propertyPath,
                                                                  @NonNull Class<?> type) {
        String[] parts = Arrays.stream(propertyPath.replace('.', '/').split("/"))
                .filter(s -> !s.isBlank()).toArray(String[]::new);
        if (parts.length == 1) {
            return computeSetter(parts[0], type);
        }
        Function<Object, Object> parentSupplier = gettersCache.apply(
                Arrays.stream(parts).limit(parts.length - 1).collect(Collectors.joining("/")), type);
        return (object, value) -> {
            Object parent = parentSupplier.apply(object);
            if (parent != null) {
                BiConsumer<Object, Object> setter = settersCache.apply(parts[parts.length - 1], parent.getClass());
                setter.accept(parent, value);
            }
        };
    }

    @SneakyThrows
    private static BiConsumer<Object, Object> computeSetter(@NonNull String propertyName, @NonNull Class<?> type) {
        return Arrays.stream(getBeanInfo(type, Object.class).getPropertyDescriptors())
                .filter(d -> propertyName.equals(d.getName()))
                .<AccessibleObject>map(PropertyDescriptor::getWriteMethod).filter(Objects::nonNull).findFirst()
                .or(() -> Optional.ofNullable(FieldUtils.getField(type, propertyName, true)))
                .<BiConsumer<Object, Object>>map(a -> (target, value) -> setValue(a, target, value))
                .orElseThrow(() -> new PropertyNotFoundException(propertyName, type));
    }

    @SneakyThrows
    private static void setValue(AccessibleObject fieldOrMethod, Object target, Object value) {
        ensureAccessible(fieldOrMethod);
        if (fieldOrMethod instanceof Method) {
            ((Method) fieldOrMethod).invoke(target, value);
        } else if (fieldOrMethod instanceof Field) {
            ((Field) fieldOrMethod).set(target, value);
        } else {
            throw new IllegalStateException("Object property should be field or method: " + fieldOrMethod);
        }
    }

    public static boolean isOrHas(Annotation annotation, Class<? extends Annotation> annotationType) {
        return annotation != null && (Objects.equals(annotation.annotationType(), annotationType)
                || annotation.annotationType().isAnnotationPresent(annotationType));
    }

    @Value
    private static class PropertyNotFoundException extends RuntimeException {
        @NonNull String propertyName;
        @NonNull Class<?> type;
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

    public static Collection<? extends Annotation> getTypeAnnotations(Class<?> type) {
        return Stream.concat(Arrays.stream(type.getAnnotations()), Arrays.stream(type.getAnnotatedInterfaces())
                .map(AnnotatedType::getType).flatMap(t -> {
                    if (t instanceof ParameterizedType) {
                        t = ((ParameterizedType) t).getRawType();
                    }
                    if (t instanceof Class<?>) {
                        return Stream.of((Class<?>) t);
                    }
                    return Stream.empty();
                }).map(t -> (Class<?>) t)
                .flatMap(i -> Arrays.stream(i.getAnnotations()))).collect(toCollection(LinkedHashSet::new));
    }

    public static Class<?> classForName(String type) {
        return classForNameCache.apply(type);
    }

    /*
       Adopted from https://stackoverflow.com/questions/49105303/how-to-get-annotation-from-overridden-method-in-java/49164791
    */
    @SuppressWarnings("unchecked")
    public static <A extends Annotation> Optional<A> getMethodAnnotation(Executable m, Class<A> a) {
        if (a == null) {
            return Optional.empty();
        }
        Annotation result = getTopLevelAnnotation(m, a);
        Class<?> c = m.getDeclaringClass();

        if (result == null) {
            for (Class<?> s = c; result == null && (s = s.getSuperclass()) != null; ) {
                result = getAnnotationOnSuper(m, s, a);
            }
            if (result == null && m instanceof Method) {
                for (Class<?> s : getAllInterfaces(c)) {
                    result = getAnnotationOnSuper(m, s, a);
                    if (result != null) {
                        break;
                    }
                }
            }
        }
        return Optional.ofNullable((A) result);
    }

    private static Annotation getTopLevelAnnotation(Executable m, Class<? extends Annotation> a) {
        return Optional.<Annotation>ofNullable(m.getAnnotation(a)).orElseGet(() -> Arrays.stream(m.getAnnotations())
                .filter(other -> other.annotationType().isAnnotationPresent(a)).findFirst().orElse(null));
    }

    private static Annotation getAnnotationOnSuper(Executable m, Class<?> s, Class<? extends Annotation> a) {
        try {
            Method n = s.getDeclaredMethod(m.getName(), m.getParameterTypes());
            return overrides(m, n) ? getTopLevelAnnotation(n, a) : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static boolean overrides(Executable a, Executable b) {
        int modsA = a.getModifiers(), modsB = b.getModifiers();
        if (Modifier.isPrivate(modsA) || Modifier.isPrivate(modsB)) {
            return false;
        }
        if (Modifier.isStatic(modsA) || Modifier.isStatic(modsB)) {
            return false;
        }
        if (Modifier.isFinal(modsB)) {
            return false;
        }
        if (compareAccess(modsA, modsB) < 0) {
            return false;
        }
        return (notPackageAccess(modsA) && notPackageAccess(modsB))
                || a.getDeclaringClass().getPackage().equals(b.getDeclaringClass().getPackage());
    }

    private static boolean notPackageAccess(int mods) {
        return (mods & ACCESS_MODIFIERS) != 0;
    }

    private static int compareAccess(int lhs, int rhs) {
        return compare(ACCESS_ORDER.indexOf(lhs & ACCESS_MODIFIERS), ACCESS_ORDER.indexOf(rhs & ACCESS_MODIFIERS));
    }

    public static boolean classExists(String className) {
        try {
            ReflectionUtils.classForName(className);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SneakyThrows
    private static Class<?> computeClass(String type) {
        return Class.forName(type.split("<")[0]);
    }
}

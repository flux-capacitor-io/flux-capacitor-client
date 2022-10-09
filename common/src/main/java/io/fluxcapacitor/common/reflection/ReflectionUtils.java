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

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxcapacitor.common.serialization.JsonUtils;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
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
import java.util.Iterator;
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
import static io.fluxcapacitor.common.reflection.DefaultMemberInvoker.asInvoker;
import static java.beans.Introspector.getBeanInfo;
import static java.lang.Integer.compare;
import static java.lang.String.format;
import static java.security.AccessController.doPrivileged;
import static java.util.Arrays.stream;
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
    private static final BiFunction<Class<?>, Class<? extends Annotation>, List<? extends AccessibleObject>>
            annotatedPropertiesCache = memoize(ReflectionUtils::computeAnnotatedProperties);
    private static final BiFunction<Class<?>, String, Function<Object, Object>> gettersCache =
            memoize(ReflectionUtils::computeNestedGetter);
    private static final BiFunction<String, Class<?>, BiConsumer<Object, Object>> settersCache =
            memoize(ReflectionUtils::computeNestedSetter);
    private static final Function<Parameter, Boolean> isNullableCache = memoize(
            parameter -> {
                if (isKotlinReflectionSupported()) {
                    var kotlinParameter = KotlinReflectionUtils.asKotlinParameter(parameter);
                    if (kotlinParameter != null && kotlinParameter.getType().isMarkedNullable()) {
                        return true;
                    }
                }
                return stream(parameter.getAnnotations()).anyMatch(
                        a -> a.annotationType().getSimpleName().equals("Nullable"));
            }
    );

    public static boolean isKotlinReflectionSupported() {
        return ReflectionUtils.classExists("kotlin.reflect.full.KClasses");
    }

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

    public static List<?> getAnnotatedPropertyValues(Object target, Class<? extends Annotation> annotation) {
        if (target == null) {
            return emptyList();
        }
        List<Object> result = new ArrayList<>();
        for (AccessibleObject property : getAnnotatedProperties(target.getClass(), annotation)) {
            result.add(getValue(property, target));
        }
        return result;
    }

    public static List<? extends AccessibleObject> getAnnotatedProperties(Class<?> target,
                                                                          Class<? extends Annotation> annotation) {
        return annotatedPropertiesCache.apply(target, annotation);
    }

    private static List<? extends AccessibleObject> computeAnnotatedProperties(Class<?> target,
                                                                               Class<? extends Annotation> annotation) {
        List<AccessibleObject> result =
                new ArrayList<>(FieldUtils.getFieldsListWithAnnotation(target, annotation));
        result.addAll(getMethodsListWithAnnotation(target, annotation, true, true).stream()
                              .filter(m -> m.getParameterCount() == 0).collect(toList()));
        getAllInterfaces(target)
                .forEach(i -> result.addAll(FieldUtils.getFieldsListWithAnnotation(i, annotation)));
        result.forEach(ReflectionUtils::ensureAccessible);
        return result;
    }

    public static Optional<? extends AccessibleObject> getAnnotatedProperty(Object target,
                                                                            Class<? extends Annotation> annotation) {
        return target == null ? Optional.empty() : getAnnotatedProperty(target.getClass(), annotation);
    }

    public static Optional<? extends AccessibleObject> getAnnotatedProperty(Class<?> target,
                                                                            Class<? extends Annotation> annotation) {
        List<? extends AccessibleObject> annotatedProperties = getAnnotatedProperties(target, annotation);
        return annotatedProperties.isEmpty() ? Optional.empty() : Optional.of(annotatedProperties.get(0));
    }

    public static Optional<?> getAnnotatedPropertyValue(Object target, Class<? extends Annotation> annotation) {
        return getAnnotatedProperty(target, annotation).map(m -> getValue(m, target, false));
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
            return Optional.ofNullable(gettersCache.apply(target.getClass(), propertyPath).apply(target))
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
            gettersCache.apply(target.getClass(), propertyPath).apply(target);
            return true;
        } catch (PropertyNotFoundException ignored) {
            return false;
        }
    }

    private static Function<Object, Object> computeNestedGetter(Class<?> type, String propertyPath) {
        String[] parts = stream(propertyPath.replace('.', '/').split("/"))
                .filter(s -> !s.isBlank()).toArray(String[]::new);
        if (parts.length == 1) {
            return computeGetter(type, parts[0]);
        }
        return object -> {
            for (String part : parts) {
                if (object == null) {
                    return null;
                }
                object = gettersCache.apply(object.getClass(), part).apply(object);
            }
            return object;
        };
    }

    @SneakyThrows
    private static Function<Object, Object> computeGetter(@NonNull Class<?> type, @NonNull String propertyName) {
        if (ObjectNode.class.isAssignableFrom(type)) {
            return target -> ((ObjectNode) target).get(propertyName);
        }
        PropertyNotFoundException notFoundException = new PropertyNotFoundException(propertyName, type);
        return Optional.<Member>ofNullable(
                        MethodUtils.getMatchingMethod(type, "get" + StringUtils.capitalize(propertyName)))
                .or(() -> Optional.ofNullable(MethodUtils.getMatchingMethod(type, propertyName)))
                .or(() -> Optional.ofNullable(FieldUtils.getField(type, propertyName, true)))
                .map(DefaultMemberInvoker::asInvoker)
                .<Function<Object, Object>>map(invoker -> invoker::invoke)
                .orElseGet(() -> o -> {
                    throw notFoundException;
                });
    }

    @SneakyThrows
    public static Object getValue(AccessibleObject fieldOrMethod, Object target, boolean forceAccess) {
        if (fieldOrMethod instanceof Method) {
            return asInvoker((Method) fieldOrMethod, forceAccess).invoke(target);
        }
        if (fieldOrMethod instanceof Field) {
            return asInvoker((Field) fieldOrMethod, forceAccess).invoke(target);
        }
        throw new IllegalStateException("Object property should be field or method: " + fieldOrMethod);
    }

    @SneakyThrows
    public static Object getValue(AccessibleObject fieldOrMethod, Object target) {
        return getValue(fieldOrMethod, target, true);
    }

    @SneakyThrows
    public static String getName(AccessibleObject fieldOrMethod) {
        if (fieldOrMethod instanceof Member) {
            return ((Member) fieldOrMethod).getName();
        }
        throw new IllegalStateException("Object property should be field or method: " + fieldOrMethod);
    }

    @SneakyThrows
    public static Class<?> getEnclosingClass(AccessibleObject fieldOrMethod) {
        if (fieldOrMethod instanceof Member) {
            return ((Member) fieldOrMethod).getDeclaringClass();
        }
        throw new IllegalStateException("Object property should be field or method: " + fieldOrMethod);
    }

    public static Class<?> getPropertyType(AccessibleObject fieldOrMethod) {
        if (fieldOrMethod instanceof Method) {
            return ((Method) fieldOrMethod).getReturnType();
        }
        if (fieldOrMethod instanceof Field) {
            return ((Field) fieldOrMethod).getType();
        }
        throw new IllegalStateException("Object property should be field or method: " + fieldOrMethod);
    }

    public static Type getGenericPropertyType(AccessibleObject fieldOrMethod) {
        if (fieldOrMethod instanceof Method) {
            return ((Method) fieldOrMethod).getGenericReturnType();
        }
        if (fieldOrMethod instanceof Field) {
            return ((Field) fieldOrMethod).getGenericType();
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
        String[] parts = stream(propertyPath.replace('.', '/').split("/"))
                .filter(s -> !s.isBlank()).toArray(String[]::new);
        if (parts.length == 1) {
            return computeSetter(parts[0], type);
        }
        Function<Object, Object> parentSupplier = gettersCache.apply(
                type, stream(parts).limit(parts.length - 1).collect(Collectors.joining("/")));
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
        if (ObjectNode.class.isAssignableFrom(type)) {
            return (target, propertyValue) -> ((ObjectNode) target).putPOJO(propertyName, propertyValue);
        }
        PropertyNotFoundException notFoundException = new PropertyNotFoundException(propertyName, type);
        return stream(getBeanInfo(type, Object.class).getPropertyDescriptors())
                .filter(d -> propertyName.equals(d.getName()))
                .<Member>map(PropertyDescriptor::getWriteMethod).filter(Objects::nonNull).findFirst()
                .or(() -> Optional.ofNullable(FieldUtils.getField(type, propertyName, true)))
                .map(DefaultMemberInvoker::asInvoker)
                .<BiConsumer<Object, Object>>map(invoker -> invoker::invoke)
                .orElseGet(() -> (t, v) -> {
                    throw notFoundException;
                });
    }

    @SneakyThrows
    private static void setValue(Member fieldOrMethod, Object target, Object value) {
        asInvoker(fieldOrMethod).invoke(target, value);
    }

    public static boolean isOrHas(Annotation annotation, Class<? extends Annotation> annotationType) {
        return annotation != null && (Objects.equals(annotation.annotationType(), annotationType)
                                      || annotation.annotationType().isAnnotationPresent(annotationType));
    }

    @SneakyThrows
    public static Class<?> getPropertyType(Class<?> target, String propertyName) {
        Field field = FieldUtils.getField(target, propertyName);
        return field != null ? field.getType() :
                stream(getBeanInfo(target, Object.class).getPropertyDescriptors())
                        .filter(d -> propertyName.equals(d.getName()))
                        .map(PropertyDescriptor::getPropertyType).findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                format("Property %s could not be found on target class %s", propertyName, target)));
    }

    public static Optional<Field> getField(Class<?> owner, String name) {
        while (owner != null) {
            for (Field declaredField : owner.getDeclaredFields()) {
                if (declaredField.getName().equals(name)) {
                    return Optional.of(declaredField);
                }
            }
            owner = owner.getSuperclass();
        }
        return Optional.empty();
    }

    public static Class<?> getCallerClass() {
        return StackWalker.getInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE))
                .walk(s -> {
                    Iterator<StackWalker.StackFrame> iterator = s.skip(1).iterator();
                    Class<?> invoker = iterator.next().getDeclaringClass();
                    while (iterator.hasNext()) {
                        StackWalker.StackFrame frame = iterator.next();
                        var frameClass = frame.getDeclaringClass();
                        if (!frameClass.equals(invoker)
                            && !frameClass.getName().startsWith("java.")) {
                            return frameClass;
                        }
                    }
                    return null;
                });
    }

    public static boolean isNullable(Parameter parameter) {
        return isNullableCache.apply(parameter);
    }

    @SuppressWarnings("unchecked")
    public static <T> T asInstance(Object classOrInstance) {
        if (classOrInstance instanceof Class<?>) {
            try {
                return (T) ensureAccessible(((Class<?>) classOrInstance).getDeclaredConstructor()).newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(format(
                        "Failed to create an instance of class %s. Does it have an accessible default constructor?",
                        classOrInstance), e);
            }
        }
        return (T) classOrInstance;
    }

    @Value
    private static class PropertyNotFoundException extends RuntimeException {
        @NonNull String propertyName;
        @NonNull Class<?> type;
    }

    public static Optional<Class<?>> getCollectionElementType(AccessibleObject fieldOrMethod) {
        if (fieldOrMethod instanceof Method) {
            return getCollectionElementType(((Method) fieldOrMethod).getGenericReturnType());
        }
        if (fieldOrMethod instanceof Field) {
            return getCollectionElementType(((Field) fieldOrMethod).getGenericType());
        }
        throw new IllegalStateException("Object property should be field or method: " + fieldOrMethod);
    }

    public static Optional<Class<?>> getCollectionElementType(Type parameterizedType) {
        if (parameterizedType instanceof ParameterizedType) {
            Type elementType;
            Type rawType = ((ParameterizedType) parameterizedType).getRawType();
            if (rawType instanceof Class<?> && Map.class.isAssignableFrom((Class<?>) rawType)) {
                elementType = ((ParameterizedType) parameterizedType).getActualTypeArguments()[1];
            } else {
                elementType = ((ParameterizedType) parameterizedType).getActualTypeArguments()[0];
            }
            if (elementType instanceof WildcardType) {
                Type[] upperBounds = ((WildcardType) elementType).getUpperBounds();
                elementType = upperBounds.length > 0 ? upperBounds[0] : null;
            }
            return Optional.of(elementType instanceof Class<?> ? (Class<?>) elementType : Object.class);
        }
        return Optional.empty();
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
        return Stream.concat(stream(type.getAnnotations()), stream(type.getAnnotatedInterfaces())
                .map(AnnotatedType::getType).flatMap(t -> {
                    if (t instanceof ParameterizedType) {
                        t = ((ParameterizedType) t).getRawType();
                    }
                    if (t instanceof Class<?>) {
                        return Stream.of((Class<?>) t);
                    }
                    return Stream.empty();
                }).map(t -> (Class<?>) t)
                .flatMap(i -> stream(i.getAnnotations()))).collect(toCollection(LinkedHashSet::new));
    }

    public static Class<?> classForName(String type) {
        return classForNameCache.apply(type);
    }

    /*
        Returns meta annotation if desired
     */
    public static <A extends Annotation> Optional<A> getAnnotation(Executable m, Class<A> a) {
        return getAnnotationAs(m, a, a);
    }

    /*
        Returns any object
     */
    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> getAnnotationAs(Executable m, Class<? extends Annotation> a,
                                                  Class<T> returnType) {
        if (a == null) {
            return Optional.empty();
        }
        Annotation result = getMethodAnnotation(m, a).orElse(null);
        if (result == null) {
            return Optional.empty();
        }
        if (a.equals(returnType)) {
            if (result.annotationType().equals(returnType)) {
                return Optional.of((T) result);
            }
            return Optional.of((T) result.annotationType().getAnnotation(a));
        }
        Class<? extends Annotation> matchedType = result.annotationType();
        Map<String, Object> params = new HashMap<>();
        if (!matchedType.equals(a)) {
            var typeAnnotation = matchedType.getAnnotation(a);
            for (Method method : a.getDeclaredMethods()) {
                params.put(method.getName(), method.invoke(typeAnnotation));
            }
        }
        for (Method method : matchedType.getDeclaredMethods()) {
            params.put(method.getName(), method.invoke(result));
        }
        if (Map.class.equals(returnType)) {
            return Optional.of((T) params);
        }
        return Optional.of(JsonUtils.convertValue(params, returnType));
    }

    public static boolean has(Class<? extends Annotation> annotationClass, Method method) {
        return getMethodAnnotation(method, annotationClass).isPresent();
    }

    /*
       Adopted from https://stackoverflow.com/questions/49105303/how-to-get-annotation-from-overridden-method-in-java/49164791
    */
    public static Optional<? extends Annotation> getMethodAnnotation(Executable m, Class<? extends Annotation> a) {
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
        return Optional.ofNullable(result);
    }

    private static Annotation getTopLevelAnnotation(Executable m, Class<? extends Annotation> a) {
        return Optional.<Annotation>ofNullable(m.getAnnotation(a)).orElseGet(() -> stream(m.getAnnotations())
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
    public static <V> V copyFields(V source, V target) {
        if (target == null || source == null) {
            return target;
        }
        if (!source.getClass().equals(target.getClass())) {
            throw new IllegalArgumentException("Source and target class should be equal");
        }
        Class<?> type = source.getClass();
        if (type.isPrimitive() || type.isArray()) {
            return source;
        }
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                ensureAccessible(field).set(target, field.get(source));
            }
            type = type.getSuperclass();
        }
        return target;
    }

    @SneakyThrows
    private static Class<?> computeClass(String type) {
        return Class.forName(type.split("<")[0]);
    }
}

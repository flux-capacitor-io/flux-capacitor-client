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

package io.fluxcapacitor.common.reflection;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.serialization.JsonUtils;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

import java.beans.PropertyDescriptor;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.net.URL;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
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
            parameter -> getParameterOverrideHierarchy(parameter).anyMatch(p -> {
                if (isKotlinReflectionSupported()) {
                    var kotlinParameter = KotlinReflectionUtils.asKotlinParameter(p);
                    if (kotlinParameter != null && kotlinParameter.getType().isMarkedNullable()) {
                        return true;
                    }
                }
                return stream(p.getAnnotations()).anyMatch(
                        a -> a.annotationType().getSimpleName().equals("Nullable"));
            })
    );

    private static final Function<Class<?>, Collection<? extends Annotation>> typeAnnotations = memoize(
            ReflectionUtils::computeTypeAnnotations);

    @Getter
    private static final Comparator<Class<?>> classSpecificityComparator = (o1, o2)
            -> Objects.equals(o1, o2) ? 0
            : o1 == null ? 1 : o2 == null ? -1
            : o1.isAssignableFrom(o2) ? 1
            : o2.isAssignableFrom(o1) ? -1
            : o1.isInterface() && !o2.isInterface() ? 1
            : !o1.isInterface() && o2.isInterface() ? -1
            : specificity(o2) - specificity(o1);

    static int specificity(Class<?> type) {
        int depth = 0;
        Class<?> t = type;
        if (type.isInterface()) {
            while (t.getInterfaces().length > 0) {
                depth++;
                t = t.getInterfaces()[0];
            }
        } else {
            while (t != null) {
                depth++;
                t = t.getSuperclass();
            }
        }
        return depth;
    }


    public static Stream<Method> getMethodOverrideHierarchy(Method method) {
        return MethodUtils.getOverrideHierarchy(method, ClassUtils.Interfaces.INCLUDE).stream();
    }

    public static Stream<Parameter> getParameterOverrideHierarchy(Parameter parameter) {
        if (parameter.getDeclaringExecutable() instanceof Method method) {
            return getMethodOverrideHierarchy(method).flatMap(m -> Arrays.stream(m.getParameters())
                    .filter(p -> p.getName().equals(parameter.getName())));
        }
        return Stream.of(parameter);
    }

    public static boolean isKotlinReflectionSupported() {
        return ReflectionUtils.classExists("kotlin.reflect.full.KClasses");
    }

    public static Class<?> ifClass(Object value) {
        if (value instanceof Class<?> c) {
            return c;
        }
        if (isKotlinReflectionSupported()) {
            return KotlinReflectionUtils.convertIfKotlinClass(value);
        }
        return null;
    }

    public static List<Method> getAllMethods(Class<?> type) {
        return methodsCache.apply(type);
    }

    public static Optional<Method> getMethod(Class<?> type, String name) {
        for (Method method : getAllMethods(type)) {
            if (name.equals(method.getName())) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
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

    public static List<? extends AccessibleObject> getAnnotatedProperties(Class<?> target,
                                                                          Class<? extends Annotation> annotation) {
        return annotatedPropertiesCache.apply(target, annotation);
    }

    private static List<? extends AccessibleObject> computeAnnotatedProperties(Class<?> target,
                                                                               Class<? extends Annotation> annotation) {
        List<AccessibleObject> result =
                new ArrayList<>(FieldUtils.getFieldsListWithAnnotation(target, annotation));
        result.addAll(getMethodsListWithAnnotation(target, annotation, true, true).stream()
                              .filter(m -> m.getParameterCount() == 0)
                              .filter(m -> !m.getDeclaringClass().isAssignableFrom(m.getReturnType())).toList());
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

    public static Optional<Object> getAnnotatedPropertyValue(Object target, Class<? extends Annotation> annotation) {
        return getAnnotatedProperty(target, annotation).map(m -> getValue(m, target, false));
    }

    public static Collection<Object> getAnnotatedPropertyValues(Object target, Class<? extends Annotation> annotation) {
        if (target == null) {
            return emptyList();
        }
        List<Object> results = new ArrayList<>();
        for (AccessibleObject location : getAnnotatedProperties(target.getClass(), annotation)) {
            Object value = getValue(location, target, false);
            if (value != null) {
                results.add(value);
            }
        }
        return results;
    }

    public static String getPropertyName(AccessibleObject property) {
        if (property instanceof Field field) {
            return field.getName();
        }
        if (property instanceof Method method) {
            String name = method.getName();
            if (name.length() > 3 && name.startsWith("get") && Character.isUpperCase(name.charAt(3))) {
                char[] c = name.toCharArray();
                c[3] = Character.toLowerCase(c[3]);
                name = String.valueOf(c, 3, c.length - 3);
            } else if (name.length() > 2 && name.startsWith("is") && Character.isUpperCase(name.charAt(2))) {
                char[] c = name.toCharArray();
                c[2] = Character.toLowerCase(c[2]);
                name = String.valueOf(c, 2, c.length - 2);
            }
            return name;
        }
        throw new UnsupportedOperationException("Not a property: " + property);
    }

    public static List<Method> getAnnotatedMethods(Class<?> target, Class<? extends Annotation> annotation) {
        return methodsCache.apply(target).stream().filter(m -> getMethodAnnotation(m, annotation).isPresent()).toList();
    }

    public static List<Method> getAnnotatedMethods(Object target, Class<? extends Annotation> annotation) {
        return target == null ? List.of() : getAnnotatedMethods(target.getClass(), annotation);
    }

    public static boolean isMethodAnnotationPresent(Class<?> target, Class<? extends Annotation> annotation) {
        for (Method method : methodsCache.apply(target)) {
            if (getMethodAnnotation(method, annotation).isPresent()) {
                return true;
            }
        }
        return false;
    }

    public static List<Field> getAnnotatedFields(Class<?> target, Class<? extends Annotation> annotation) {
        return FieldUtils.getAllFieldsList(target).stream().filter(f -> getFieldAnnotation(f, annotation).isPresent())
                .toList();
    }

    public static List<Field> getAnnotatedFields(Object target, Class<? extends Annotation> annotation) {
        return target == null ? emptyList() :
                getAnnotatedFields(ifClass(target) instanceof Class<?> t ? t : target.getClass(), annotation);
    }

    public static boolean isAnnotationPresent(Class<?> type, Class<? extends Annotation> annotationType) {
        return getTypeAnnotation(type, annotationType) != null;
    }

    @SuppressWarnings("unchecked")
    public static <A extends Annotation> A getTypeAnnotation(Class<?> type, Class<? extends Annotation> annotationType) {
        for (Annotation annotation : getTypeAnnotations(type)) {
            if (annotation.annotationType().equals(annotationType)) {
                return (A) annotation;
            }
            for (Annotation metaAnnotation : getTypeAnnotations(annotation.annotationType())) {
                if (metaAnnotation.annotationType().equals(annotationType)) {
                    return (A) annotation;
                }
            }
        }
        return null;
    }

    public static Collection<? extends Annotation> getTypeAnnotations(Class<?> type) {
        return typeAnnotations.apply(type);
    }

    private static Collection<? extends Annotation> computeTypeAnnotations(Class<?> type) {
        return Stream.concat(stream(type.getAnnotations()), getAllInterfaces(type).stream()
                        .flatMap(iType -> stream(iType.getAnnotations())))
                .filter(ObjectUtils.distinctByKey(Annotation::annotationType))
                .collect(toCollection(LinkedHashSet::new));
    }

    public static <A extends Annotation> Optional<A> getPackageAnnotation(Package p, Class<A> annotationType) {
        return Optional.ofNullable(p.getAnnotation(annotationType));
    }

    public static Collection<? extends Annotation> getPackageAnnotations(Package p) {
        return getPackageAnnotations(p, true);
    }

    public static Collection<? extends Annotation> getPackageAnnotations(Package p, boolean recursive) {
        if (p == null) {
            return emptyList();
        }
        Stream<Annotation> stream = stream(p.getAnnotations());
        if (recursive) {
            stream = Stream.concat(stream, getPackageAnnotations(getFirstKnownAncestorPackage(p.getName()), true).stream());
        }
        return stream.toList();
    }

    /*
        Read a property
     */

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> readProperty(String propertyPath, Object target) {
        if (target == null) {
            return Optional.empty();
        }
        if (propertyPath == null || propertyPath.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(gettersCache.apply(target.getClass(), propertyPath).apply(target))
                    .map(v -> (T) v);
        } catch (PropertyNotFoundException ignored) {
            return Optional.empty();
        }
    }

    public static List<Type> getTypeArguments(Type genericType) {
        if (genericType instanceof ParameterizedType pt) {
            return Arrays.asList(pt.getActualTypeArguments());
        }
        return emptyList();
    }

    public static <T extends Type> T getFirstTypeArgument(Type genericType) {
        return getTypeArgument(genericType, 0);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Type> T getTypeArgument(Type genericType, int index) {
        if (genericType instanceof ParameterizedType pt) {
            return (T) pt.getActualTypeArguments()[index];
        }
        throw new IllegalArgumentException("Type is raw and does not define arguments");
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> getPropertyAnnotation(String propertyPath, Object target) {
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
        if (target == null || propertyPath == null) {
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
        return getMethod(type, "get" + StringUtils.capitalize(propertyName)).map(m -> (Member) m)
                .or(() -> getMethod(type, propertyName))
                .or(() -> getField(type, propertyName))
                .map(DefaultMemberInvoker::asInvoker)
                .<Function<Object, Object>>map(invoker -> invoker::invoke)
                .orElseGet(() -> o -> {
                    throw notFoundException;
                });
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> getFieldValue(String fieldName, Object target) {
        return target == null ? Optional.empty() :
                getField(ifClass(target) instanceof Class<?> type ? type : target.getClass(), fieldName)
                        .map(f -> (T) getValue(f, target, true));
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
        return stream(getBeanInfo(type, null).getPropertyDescriptors())
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
        if (ifClass(classOrInstance) instanceof Class<?> c) {
            try {
                return (T) ensureAccessible(c.getDeclaredConstructor()).newInstance();
            } catch (Exception e) {
                throw new IllegalStateException(format(
                        "Failed to create an instance of class %s. Does it have an accessible default constructor?",
                        classOrInstance), e);
            }
        }
        return (T) classOrInstance;
    }

    public static int getParameterIndex(Parameter parameter) {
        var executable = parameter.getDeclaringExecutable();
        for (int i = 0; i < executable.getParameters().length; i++) {
            if (executable.getParameters()[i].equals(parameter)) {
                return i;
            }
        }
        throw new IllegalStateException("Could not get parameter index of " + parameter);
    }

    /*
    Based on this SO question https://stackoverflow.com/questions/9797212/finding-the-nearest-common-superclass-or-superinterface-of-a-collection-of-cla
     */
    public static List<Class<?>> determineCommonAncestors(Collection<?> elements) {
        return determineCommonAncestors(
                elements.stream().map(e -> e == null ? Void.class : e.getClass()).distinct()
                        .toArray(Class<?>[]::new));
    }

    static List<Class<?>> determineCommonAncestors(Class<?>... classes) {
        return switch (classes.length) {
            case 0 -> Collections.emptyList();
            case 1 -> List.of(classes);
            default -> {
                Set<Class<?>> rollingIntersect = new LinkedHashSet<>(getClassHierarchy(classes[0]));
                for (int i = 1; i < classes.length; i++) {
                    rollingIntersect.retainAll(getClassHierarchy(classes[i]));
                }
                yield rollingIntersect.isEmpty() ? List.of(Object.class) : new LinkedList<>(rollingIntersect);
            }
        };
    }

    static Set<Class<?>> getClassHierarchy(Class<?> clazz) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        Set<Class<?>> nextLevel = new LinkedHashSet<>();
        nextLevel.add(clazz);
        do {
            classes.addAll(nextLevel);
            Set<Class<?>> thisLevel = new LinkedHashSet<>(nextLevel);
            nextLevel.clear();
            for (Class<?> each : thisLevel) {
                Class<?> superClass = each.getSuperclass();
                if (superClass != null && superClass != Object.class) {
                    nextLevel.add(superClass);
                }
                Collections.addAll(nextLevel, each.getInterfaces());
            }
        } while (!nextLevel.isEmpty());
        return classes;
    }

    public static List<Package> getPackageAndParentPackages(Package p) {
        List<Package> result = new ArrayList<>();
        while (p != null) {
            result.add(p);
            p = getFirstKnownAncestorPackage(p.getName());
        }
        return result;
    }

    static Package getFirstKnownAncestorPackage(String childPackageName) {
        for (String parentName = getParentPackageName(childPackageName); parentName != null;
                parentName = getParentPackageName(parentName)) {
            Package result = tryGetPackage(parentName);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    static String getParentPackageName(String name) {
        int lastIndex = name.lastIndexOf(".");
        return lastIndex < 0 ? null : name.substring(0, lastIndex);
    }

    private static Package tryGetPackage(String name) {
        Package definedPackage = ReflectionUtils.class.getClassLoader().getDefinedPackage(name);
        if (definedPackage != null) {
            return definedPackage;
        }
        String packagePath = name.replace('.', '/');
        URL resource = ReflectionUtils.class.getClassLoader().getResource(packagePath);
        if (resource == null) {
            return null;
        }
        File packageDir = new File(resource.getFile());
        if (packageDir.exists() && packageDir.isDirectory()
            && new File(resource.getFile() + "/package-info.class").exists()) {
            try {
                var result = classForName(name + ".package-info");
                if (result != null) {
                    return result.getPackage();
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    public static <A extends Annotation> Optional<A> getMemberAnnotation(Class<?> type, String memberName, Class<? extends Annotation> a) {
        return getAnnotatedMethods(type, a).stream().filter(m -> m.getName().equals(memberName)).findFirst()
                .flatMap(m -> ReflectionUtils.<A>getMethodAnnotation(m, a)).or(() -> {
                    String alias = memberName.startsWith("get") ? memberName.substring(3) :
                            memberName.startsWith("is") ? memberName.substring(2) : memberName;
                    return getAnnotatedFields(type, a).stream()
                            .filter(f -> f.getName().equalsIgnoreCase(memberName) || f.getName()
                                    .equalsIgnoreCase(alias)).findFirst()
                            .flatMap(f -> ReflectionUtils.getFieldAnnotation(f, a));
                });
    }

    public static boolean isStatic(Executable method) {
        return Modifier.isStatic(method.getModifiers());
    }

    public static boolean isConstant(Object value) {
        return value == null || value instanceof String || value instanceof Number
               || value instanceof Boolean || value.getClass().isEnum();
    }

    public static boolean isAnnotationPresent(Parameter parameter, Class<? extends Annotation> annotationType) {
        return getAnnotation(parameter, annotationType).isPresent();
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

    /*
        Returns meta annotation if desired
     */

    public static <A extends Annotation> Optional<A> getAnnotation(AnnotatedElement m, Class<A> a) {
        return getAnnotationAs(m, a, a);
    }

    /*
        Returns any object
     */
    public static <T> Optional<T> getAnnotationAs(Class<?> target, Class<? extends Annotation> annotationType,
                                                  Class<T> returnType) {
        return getAnnotationAs((Annotation) getTypeAnnotation(target, annotationType), annotationType, returnType);
    }

    public static <T> Optional<T> getAnnotationAs(AnnotatedElement member,
                                                  Class<? extends Annotation> annotationType, Class<? extends T> returnType) {
        Annotation annotation;
        if (member instanceof Executable e) {
            annotation = getMethodAnnotation(e, annotationType).orElse(null);
        } else {
            annotation = getTopLevelAnnotation(member, annotationType);
        }
        return getAnnotationAs(annotation, annotationType, returnType);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> getAnnotationAs(Annotation annotation,
                                                  Class<? extends Annotation> targetAnnotation, Class<? extends T> returnType) {
        if (targetAnnotation == null) {
            return Optional.empty();
        }
        if (annotation == null) {
            return Optional.empty();
        }
        if (targetAnnotation.equals(returnType)) {
            if (annotation.annotationType().equals(returnType)) {
                return Optional.of((T) annotation);
            }
            return Optional.of((T) annotation.annotationType().getAnnotation(targetAnnotation));
        }
        Class<? extends Annotation> matchedType = annotation.annotationType();
        Map<String, Object> params = new HashMap<>();
        if (!matchedType.equals(targetAnnotation)) {
            var typeAnnotation = matchedType.getAnnotation(targetAnnotation);
            for (Method method : targetAnnotation.getDeclaredMethods()) {
                params.put(method.getName(), method.invoke(typeAnnotation));
            }
        }
        for (Method method : matchedType.getDeclaredMethods()) {
            params.put(method.getName(), method.invoke(annotation));
        }
        if (Map.class.equals(returnType)) {
            return Optional.of((T) params);
        }
        return Optional.of(JsonUtils.convertValue(params, returnType));
    }

    public static <T> T convertAnnotation(Annotation annotation, Class<? extends T> returnType) {
        return getAnnotationAs(annotation, annotation.annotationType(), returnType).orElseThrow();
    }

    public static boolean has(Class<? extends Annotation> annotationClass, Method method) {
        return getMethodAnnotation(method, annotationClass).isPresent();
    }

    public static boolean has(Class<? extends Annotation> annotationClass, Parameter parameter) {
        for (Annotation annotation : parameter.getAnnotations()) {
            if (isOrHas(annotation, annotationClass)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static <A extends Annotation> Optional<A> getFieldAnnotation(Field f, Class<? extends Annotation> a) {
        return Optional.ofNullable((A) f.getAnnotation(a)).or(() -> stream(f.getAnnotations())
                        .filter(metaAnnotation -> metaAnnotation.annotationType().isAnnotationPresent(a))
                .findFirst().map(hit -> (A) hit));
    }

    /*
       Adopted from https://stackoverflow.com/questions/49105303/how-to-get-annotation-from-overridden-method-in-java/49164791
       
       Returns annotation or meta annotation.
    */
    public static <A extends Annotation> Optional<A> getMethodAnnotation(Executable m, Class<? extends Annotation> a) {
        A result = getTopLevelAnnotation(m, a);
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

    public static <A extends Annotation> List<A> getMethodAnnotations(Executable m, Class<? extends Annotation> a) {
        List<A> result = getTopLevelAnnotations(m, a);
        Class<?> c = m.getDeclaringClass();

        if (result.isEmpty()) {
            for (Class<?> s = c; result.isEmpty() && (s = s.getSuperclass()) != null; ) {
                result = getAnnotationsOnSuper(m, s, a);
            }
            if (result.isEmpty() && m instanceof Method) {
                for (Class<?> s : getAllInterfaces(c)) {
                    result = getAnnotationsOnSuper(m, s, a);
                    if (result != null) {
                        break;
                    }
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <A extends Annotation> A getTopLevelAnnotation(AnnotatedElement m, Class<? extends Annotation> a) {
        return Optional.ofNullable((A) m.getAnnotation(a)).orElseGet(() -> (A) stream(m.getAnnotations())
                .filter(metaAnnotation -> metaAnnotation.annotationType().isAnnotationPresent(a)).findFirst()
                .orElse(null));
    }

    @SuppressWarnings("unchecked")
    private static <A extends Annotation> List<A> getTopLevelAnnotations(AnnotatedElement m, Class<? extends Annotation> a) {
        return Optional.ofNullable(m.getAnnotation(a)).map(v -> Stream.of((A) v))
                .orElseGet(() -> stream(m.getAnnotations())
                        .filter(metaAnnotation -> metaAnnotation.annotationType().isAnnotationPresent(a))
                        .map(v -> (A) v))
                .collect(toCollection(ArrayList::new));
    }

    private static <A extends Annotation> A getAnnotationOnSuper(
            Executable m, Class<?> s, Class<? extends Annotation> a) {
        try {
            for (Method n : s.getDeclaredMethods()) {
                if (n.getName().equals(m.getName()) && n.getParameterCount() == m.getParameterCount()) {
                    n = s.getDeclaredMethod(m.getName(), m.getParameterTypes());
                    return overrides(m, n) ? getTopLevelAnnotation(n, a) : null;
                }
            }
        } catch (NoSuchMethodException ignored) {
        }
        return null;
    }

    private static <A extends Annotation> List<A> getAnnotationsOnSuper(
            Executable m, Class<?> s, Class<? extends Annotation> a) {
        try {
            for (Method n : s.getDeclaredMethods()) {
                if (n.getName().equals(m.getName()) && n.getParameterCount() == m.getParameterCount()) {
                    n = s.getDeclaredMethod(m.getName(), m.getParameterTypes());
                    return overrides(m, n) ? getTopLevelAnnotations(n, a) : emptyList();
                }
            }
        } catch (NoSuchMethodException ignored) {
        }
        return emptyList();
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
                if (!Modifier.isStatic(field.getModifiers())) {
                    ensureAccessible(field).set(target, field.get(source));
                }
            }
            type = type.getSuperclass();
        }
        return target;
    }

    public static Class<?> classForName(String type) {
        return classForNameCache.apply(type);
    }

    public static Class<?> classForName(String type, Class<?> defaultClass) {
        try {
            return classForNameCache.apply(type);
        } catch (Exception ignored) {
            return defaultClass;
        }
    }

    public static boolean classExists(String className) {
        try {
            classForNameCache.apply(className);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SneakyThrows
    public static <T> Constructor<? extends T> getConstructor(Class<? extends T> type, Class<?>... arguments) {
        return type.getDeclaredConstructor(arguments);
    }

    @SneakyThrows
    private static Class<?> computeClass(String type) {
        return Class.forName(type.split("<")[0]);
    }
}

package io.fluxcapacitor.common.reflection;

import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.invoke.MethodHandles.privateLookupIn;
import static java.lang.invoke.MethodType.methodType;
import static java.util.stream.Collectors.toList;

@Slf4j
public class DefaultMemberInvoker implements MemberInvoker {

    private static final Object[] emptyArray = new Object[0];

    public static MemberInvoker asInvoker(Member member) {
        return asInvoker(member, true);
    }

    public static MemberInvoker asInvoker(Member member, boolean forceAccess) {
        return cache.computeIfAbsent(member, m -> new DefaultMemberInvoker(m, forceAccess));
    }

    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();
    private static final Map<Member, MemberInvoker> cache = new ConcurrentHashMap<>();

    private static final List<Queue<Object[]>> parameterArrays = IntStream.range(0, 10).mapToObj(
            i -> i == 0 ? new ConcurrentLinkedQueue<Object[]>() : new ConcurrentLinkedQueue<>(
                    IntStream.range(0, 10).mapToObj(j -> new Object[i]).collect(toList())))
            .collect(Collectors.toCollection(CopyOnWriteArrayList::new));

    @Getter
    private final Member member;
    private final BiFunction<Object, Object[], Object> invokeFunction;
    private final boolean invokeWithoutTarget;

    private DefaultMemberInvoker(Member member, boolean forceAccess) {
        if (forceAccess) {
            ReflectionUtils.ensureAccessible((AccessibleObject) member);
        }
        this.member = member;
        invokeWithoutTarget = Modifier.isStatic(member.getModifiers()) || member instanceof Constructor<?>;
        invokeFunction = computeInvokeFunction(member);
    }

    @Override
    public Object invoke(Object target, Object... params) {
        if (invokeWithoutTarget) {
            switch (params.length) {
                case 0: return invokeFunction.apply(null, emptyArray);
                case 1: return invokeFunction.apply(params[0], emptyArray);
                default: return invokeFunction.apply(params[0], Arrays.copyOfRange(params, 1, params.length));
            }
        }
        return target == null ? null : invokeFunction.apply(target, params);
    }

    @Override
    public Object invoke(Object target, int parameterCount, IntFunction<?> parameterProvider) {
        boolean borrowed = false;
        Object[] params;
        if (parameterCount == 0) {
            params = emptyArray;
        } else if (parameterCount >= parameterArrays.size()) {
            params = new Object[parameterCount];
        } else {
            Object[] polled = parameterArrays.get(parameterCount).poll();
            borrowed = polled != null;
            params = borrowed ? polled : new Object[parameterCount];
        }
        try {
            for (int i = 0; i < parameterCount; i++) {
                params[i] = parameterProvider.apply(i);
            }
            return invoke(target, params);
        } finally {
            if (borrowed) {
                parameterArrays.get(parameterCount).add(params);
            }
        }
    }

    private BiFunction<Object, Object[], Object> computeInvokeFunction(@NonNull Member member) {
        BiFunction<Object, Object[], Object> fallbackFunction = computeFallbackFunction(member);
        if (member instanceof Field || Proxy.isProxyClass(member.getDeclaringClass())) {
            return fallbackFunction;
        }
        try {
            var lookup = privateLookupIn(member.getDeclaringClass(), DefaultMemberInvoker.lookup);
            return member instanceof Method && ((Method) member).getReturnType().equals(void.class)
                    ? createConsumerHandle((Method) member, lookup) : createFunctionHandle(member, lookup);
        } catch (Exception e) {
            log.warn("Failed to create lambda type method invoke", e);
            return fallbackFunction;
        }
    }

    @SneakyThrows
    private static BiFunction<Object, Object[], Object> createFunctionHandle(Member member, MethodHandles.Lookup lookup) {
        MethodHandle realMethodHandle = getMethodHandle(member, lookup);

        int lambdaParameters = getLambdaParameterCount(member);
        MethodType factoryType =
                methodType(Class.forName(DefaultMemberInvoker.class.getName() + "$_Function" + lambdaParameters));
        MethodType interfaceMethodType = MethodType.methodType(
                Object.class, Collections.nCopies(lambdaParameters, Object.class).toArray(Class<?>[]::new));
        CallSite site = LambdaMetafactory.metafactory(lookup, "apply", factoryType,
                                                      interfaceMethodType, realMethodHandle, realMethodHandle.type());
        Object wrappedInvoker = site.getTarget().invoke();

        switch (lambdaParameters) {
            case 0:
                return (target, params) -> ((_Function0) wrappedInvoker).apply();
            case 1:
                return (target, params) -> ((_Function1) wrappedInvoker).apply(target);
            case 2:
                return (target, params) -> ((_Function2) wrappedInvoker).apply(target, params[0]);
            case 3:
                return (target, params) -> ((_Function3) wrappedInvoker).apply(target, params[0], params[1]);
            case 4:
                return (target, params) -> ((_Function4) wrappedInvoker).apply(target, params[0], params[1], params[2]);
            case 5:
                return (target, params) -> ((_Function5) wrappedInvoker).apply(
                        target, params[0], params[1], params[2], params[3]);
            case 6:
                return (target, params) -> ((_Function6) wrappedInvoker).apply(
                        target, params[0], params[1], params[2], params[3], params[4]);
            case 7:
                return (target, params) -> ((_Function7) wrappedInvoker).apply(
                        target, params[0], params[1], params[2], params[3], params[4], params[5]);
            case 8:
                return (target, params) -> ((_Function8) wrappedInvoker).apply(
                        target, params[0], params[1], params[2], params[3], params[4], params[5], params[6]);
            case 9:
                return (target, params) -> ((_Function9) wrappedInvoker).apply(
                        target, params[0], params[1], params[2], params[3], params[4], params[5], params[6], params[7]);
            case 10:
                return (target, params) -> ((_Function10) wrappedInvoker).apply(
                        target, params[0], params[1], params[2], params[3], params[4], params[5], params[6], params[7],
                        params[8]);
            default:
                throw new UnsupportedOperationException();
        }
    }

    @SneakyThrows
    private static BiFunction<Object, Object[], Object> createConsumerHandle(Method method, MethodHandles.Lookup lookup) {
        MethodHandle realMethodHandle = getMethodHandle(method, lookup);
        int lambdaParameters = getLambdaParameterCount(method);

        MethodType factoryType =
                methodType(Class.forName(DefaultMemberInvoker.class.getName() + "$_Consumer" + lambdaParameters));
        MethodType interfaceMethodType = methodType(
                void.class, Collections.nCopies(lambdaParameters, Object.class).toArray(Class<?>[]::new));
        CallSite site = LambdaMetafactory.metafactory(
                lookup, "accept", factoryType,
                interfaceMethodType, realMethodHandle, realMethodHandle.type());
        Object wrappedInvoker = site.getTarget().invoke();

        switch (lambdaParameters) {
            case 0:
                return (target, params) -> {
                    ((_Consumer0) wrappedInvoker).accept();
                    return null;
                };
            case 1:
                return (target, params) -> {
                    ((_Consumer1) wrappedInvoker).accept(target);
                    return null;
                };
            case 2:
                return (target, params) -> {
                    ((_Consumer2) wrappedInvoker).accept(target, params[0]);
                    return null;
                };
            case 3:
                return (target, params) -> {
                    ((_Consumer3) wrappedInvoker).accept(target, params[0], params[1]);
                    return null;
                };
            case 4:
                return (target, params) -> {
                    ((_Consumer4) wrappedInvoker).accept(target, params[0], params[1], params[2]);
                    return null;
                };
            case 5:
                return (target, params) -> {
                    ((_Consumer5) wrappedInvoker).accept(target, params[0], params[1], params[2], params[3]);
                    return null;
                };
            case 6:
                return (target, params) -> {
                    ((_Consumer6) wrappedInvoker).accept(target, params[0], params[1], params[2], params[3], params[4]);
                    return null;
                };
            case 7:
                return (target, params) -> {
                    ((_Consumer7) wrappedInvoker).accept(target, params[0], params[1], params[2], params[3], params[4], params[5]);
                    return null;
                };
            case 8:
                return (target, params) -> {
                    ((_Consumer8) wrappedInvoker).accept(target, params[0], params[1], params[2], params[3], params[4], params[5], params[6]);
                    return null;
                };
            case 9:
                return (target, params) -> {
                    ((_Consumer9) wrappedInvoker).accept(target, params[0], params[1], params[2], params[3], params[4], params[5], params[6], params[7]);
                    return null;
                };
            case 10:
                return (target, params) -> {
                    ((_Consumer10) wrappedInvoker).accept(target, params[0], params[1], params[2], params[3], params[4], params[5], params[6], params[7], params[8]);
                    return null;
                };
            default:
                throw new UnsupportedOperationException();
        }
    }

    private static int getLambdaParameterCount(Member member) {
        if (member instanceof Method) {
            return ((Method) member).getParameterCount() + (Modifier.isStatic(member.getModifiers()) ? 0 : 1);
        }
        if (member instanceof Field) {
            return Modifier.isStatic(member.getModifiers()) ? 0 : 1;
        }
        if (member instanceof Constructor<?>) {
            return ((Constructor<?>) member).getParameterCount();
        }
        throw new UnsupportedOperationException("Member type not supported: " + member.getClass());
    }

    private static MethodHandle getMethodHandle(Member member, MethodHandles.Lookup lookup) throws IllegalAccessException {
        if (member instanceof Method) {
            return lookup.unreflect((Method) member);
        }
        if (member instanceof Field) {
            return lookup.unreflectGetter((Field) member);
        }
        if (member instanceof Constructor<?>) {
            return lookup.unreflectConstructor((Constructor<?>) member);
        }
        throw new UnsupportedOperationException("Member type not supported: " + member.getClass());
    }

    @SuppressWarnings({"Convert2Lambda", "Anonymous2MethodRef"})
    private BiFunction<Object, Object[], Object> computeFallbackFunction(Member member) {
        if (member instanceof Method) {
            Method method = (Method) member;
            return new BiFunction<>() {
                @Override
                @SneakyThrows
                public Object apply(Object target, Object[] params) {
                    return method.invoke(target, params);
                }
            };
        }
        if (member instanceof Field) {
            Field field = (Field) member;
            return new BiFunction<>() {
                @Override
                @SneakyThrows
                public Object apply(Object target, Object[] params) {
                    if (params.length == 0) {
                        return field.get(target);
                    }
                    field.set(target, params[0]);
                    return target;
                }
            };
        }
        if (member instanceof Constructor) {
            Constructor<?> constructor = (Constructor<?>) member;
            return new BiFunction<>() {
                @Override
                @SneakyThrows
                public Object apply(Object target, Object[] params) {
                    return constructor.newInstance(params);
                }
            };
        }
        throw new UnsupportedOperationException("Member type not supported: " + member.getClass());
    }

    @FunctionalInterface
    public interface _Consumer0 {
        void accept();
    }

    @FunctionalInterface
    public interface _Consumer1 {
        void accept(Object p1);
    }

    @FunctionalInterface
    public interface _Consumer2 {
        void accept(Object p1, Object p2);
    }

    @FunctionalInterface
    public interface _Consumer3 {
        void accept(Object p1, Object p2, Object p3);
    }

    @FunctionalInterface
    public interface _Consumer4 {
        void accept(Object p1, Object p2, Object p3, Object p4);
    }

    @FunctionalInterface
    public interface _Consumer5 {
        void accept(Object p1, Object p2, Object p3, Object p4, Object p5);
    }

    @FunctionalInterface
    public interface _Consumer6 {
        void accept(Object p1, Object p2, Object p3, Object p4, Object p5, Object p6);
    }

    @FunctionalInterface
    public interface _Consumer7 {
        void accept(Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7);
    }

    @FunctionalInterface
    public interface _Consumer8 {
        void accept(Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8);
    }

    @FunctionalInterface
    public interface _Consumer9 {
        void accept(Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9);
    }

    @FunctionalInterface
    public interface _Consumer10 {
        void accept(Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9, Object p10);
    }

    @FunctionalInterface
    public interface _Function0 {
        Object apply();
    }

    @FunctionalInterface
    public interface _Function1 {
        Object apply(Object p1);
    }

    @FunctionalInterface
    public interface _Function2 {
        Object apply(Object p1, Object p2);
    }

    @FunctionalInterface
    public interface _Function3 {
        Object apply(Object p1, Object p2, Object p3);
    }

    @FunctionalInterface
    public interface _Function4 {
        Object apply(Object p1, Object p2, Object p3, Object p4);
    }

    @FunctionalInterface
    public interface _Function5 {
        Object apply(Object p1, Object p2, Object p3, Object p4, Object p5);
    }

    @FunctionalInterface
    public interface _Function6 {
        Object apply(Object p1, Object p2, Object p3, Object p4, Object p5, Object p6);
    }

    @FunctionalInterface
    public interface _Function7 {
        Object apply(Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7);
    }

    @FunctionalInterface
    public interface _Function8 {
        Object apply(Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8);
    }

    @FunctionalInterface
    public interface _Function9 {
        Object apply(Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9);
    }

    @FunctionalInterface
    public interface _Function10 {
        Object apply(Object p1, Object p2, Object p3, Object p4, Object p5, Object p6, Object p7, Object p8, Object p9,
                     Object p10);
    }


}

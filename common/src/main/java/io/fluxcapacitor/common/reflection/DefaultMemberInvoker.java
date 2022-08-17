package io.fluxcapacitor.common.reflection;

import lombok.Getter;
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
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.IntFunction;

import static java.lang.invoke.MethodHandles.privateLookupIn;
import static java.lang.invoke.MethodType.methodType;

@Slf4j
public class DefaultMemberInvoker implements MemberInvoker {

    public static MemberInvoker asInvoker(Member member) {
        return asInvoker(member, true);
    }

    public static MemberInvoker asInvoker(Member member, boolean forceAccess) {
        return cache.computeIfAbsent(member, m -> new DefaultMemberInvoker(m, forceAccess));
    }

    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();
    private static final Map<Member, MemberInvoker> cache = new ConcurrentHashMap<>();

    @Getter
    private final Member member;
    private final BiFunction<Object, IntFunction<?>, Object> invokeFunction;
    private final FallbackFunction fallbackFunction;
    private final boolean staticMember;
    private final boolean returnsResult;
    private final int lambdaParameterCount;

    private DefaultMemberInvoker(Member member, boolean forceAccess) {
        if (forceAccess) {
            ReflectionUtils.ensureAccessible((AccessibleObject) member);
        }
        this.member = member;
        lambdaParameterCount = getLambdaParameterCount(member);
        returnsResult = !(member instanceof Method && ((Method) member).getReturnType().equals(void.class));
        staticMember = Modifier.isStatic(member.getModifiers()) || member instanceof Constructor<?>;
        invokeFunction = computeInvokeFunction();
        fallbackFunction = invokeFunction == null ? computeFallbackFunction() : null;
    }

    @Override
    @SneakyThrows
    public Object invoke(Object target, int parameterCount, IntFunction<?> paramProvider) {
        if (!staticMember && target == null) {
            return null;
        }
        if (fallbackFunction != null) {
            return fallbackFunction.apply(target, parameterCount, paramProvider);
        }
        if (staticMember && parameterCount > 0) {
            return invokeFunction.apply(paramProvider.apply(0), i -> paramProvider.apply(i + 1));
        }
        return invokeFunction.apply(target, paramProvider);
    }

    @SneakyThrows
    private BiFunction<Object, IntFunction<?>, Object> computeInvokeFunction() {
        if (member instanceof Field || Proxy.isProxyClass(member.getDeclaringClass())) {
            return null;
        }
        try {
            var lookup = privateLookupIn(member.getDeclaringClass(), DefaultMemberInvoker.lookup);
            MethodHandle realMethodHandle = getMethodHandle(member, lookup);
            MethodType factoryType =
                    methodType(Class.forName(DefaultMemberInvoker.class.getName()
                                             + (returnsResult ? "$_Function" : "$_Consumer") + lambdaParameterCount));
            MethodType interfaceMethodType = methodType(returnsResult ? Object.class : void.class,
                    Collections.nCopies(lambdaParameterCount, Object.class).toArray(Class<?>[]::new));
            CallSite site = LambdaMetafactory.metafactory(
                    lookup, returnsResult ? "apply" : "accept", factoryType,
                    interfaceMethodType, realMethodHandle, realMethodHandle.type());
            Object invokeFunction = site.getTarget().invoke();

            if (returnsResult) {
                switch (lambdaParameterCount) {
                    case 0:
                        return (target, paramProvider) -> ((_Function0) invokeFunction).apply();
                    case 1:
                        return (target, paramProvider) -> ((_Function1) invokeFunction).apply(target);
                    case 2:
                        return (target, paramProvider) -> ((_Function2) invokeFunction).apply(target, paramProvider.apply(0));
                    case 3:
                        return (target, paramProvider) -> ((_Function3) invokeFunction).apply(target, paramProvider.apply(0), paramProvider.apply(1));
                    case 4:
                        return (target, paramProvider) -> ((_Function4) invokeFunction).apply(target, paramProvider.apply(0), paramProvider.apply(1), paramProvider.apply(2));
                    case 5:
                        return (target, paramProvider) -> ((_Function5) invokeFunction).apply(
                                target, paramProvider.apply(0), paramProvider.apply(1), paramProvider.apply(2), paramProvider.apply(3));
                    case 6:
                        return (target, paramProvider) -> ((_Function6) invokeFunction).apply(
                                target, paramProvider.apply(0), paramProvider.apply(1), paramProvider.apply(2), paramProvider.apply(3), paramProvider.apply(4));
                    case 7:
                        return (target, paramProvider) -> ((_Function7) invokeFunction).apply(
                                target, paramProvider.apply(0), paramProvider.apply(1), paramProvider.apply(2), paramProvider.apply(3), paramProvider.apply(4), paramProvider.apply(5));
                    case 8:
                        return (target, paramProvider) -> ((_Function8) invokeFunction).apply(
                                target, paramProvider.apply(0), paramProvider.apply(1), paramProvider.apply(2), paramProvider.apply(3), paramProvider.apply(4), paramProvider.apply(5), paramProvider.apply(6));
                    case 9:
                        return (target, paramProvider) -> ((_Function9) invokeFunction).apply(
                                target, paramProvider.apply(0), paramProvider.apply(1), paramProvider.apply(2), paramProvider.apply(3), paramProvider.apply(4), paramProvider.apply(5), paramProvider.apply(6), paramProvider.apply(7));
                    case 10:
                        return (target, paramProvider) -> ((_Function10) invokeFunction).apply(
                                target, paramProvider.apply(0), paramProvider.apply(1), paramProvider.apply(2), paramProvider.apply(3), paramProvider.apply(4), paramProvider.apply(5), paramProvider.apply(6), paramProvider.apply(7),
                                paramProvider.apply(8));
                    default:
                        throw new UnsupportedOperationException();
                }
            } else {
                switch (lambdaParameterCount) {
                    case 0:
                        return (target, paramProvider) -> {
                            ((_Consumer0) invokeFunction).accept();
                            return null;
                        };
                    case 1:
                        return (target, paramProvider) -> {
                            ((_Consumer1) invokeFunction).accept(target);
                            return null;
                        };
                    case 2:
                        return (target, paramProvider) -> {
                            ((_Consumer2) invokeFunction).accept(target, paramProvider.apply(0));
                            return null;
                        };
                    case 3:
                        return (target, paramProvider) -> {
                            ((_Consumer3) invokeFunction).accept(target, paramProvider.apply(0), paramProvider.apply(1));
                            return null;
                        };
                    case 4:
                        return (target, paramProvider) -> {
                            ((_Consumer4) invokeFunction).accept(target, paramProvider.apply(0), paramProvider.apply(1), paramProvider.apply(2));
                            return null;
                        };
                    case 5:
                        return (target, paramProvider) -> {
                            ((_Consumer5) invokeFunction).accept(
                                    target, paramProvider.apply(0), paramProvider.apply(1), paramProvider.apply(2),
                                    paramProvider.apply(3));
                            return null;
                        };
                    case 6:
                        return (target, paramProvider) -> {
                            ((_Consumer6) invokeFunction).accept(
                                    target, paramProvider.apply(0), paramProvider.apply(1), paramProvider.apply(2), paramProvider.apply(3), paramProvider.apply(4));
                            return null;
                        };
                    case 7:
                        return (target, paramProvider) -> {
                            ((_Consumer7) invokeFunction).accept(
                                    target, paramProvider.apply(0), paramProvider.apply(1), paramProvider.apply(2), paramProvider.apply(3), paramProvider.apply(4), paramProvider.apply(5));
                            return null;
                        };
                    case 8:
                        return (target, paramProvider) -> {
                            ((_Consumer8) invokeFunction).accept(
                                    target, paramProvider.apply(0), paramProvider.apply(1), paramProvider.apply(2), paramProvider.apply(3), paramProvider.apply(4), paramProvider.apply(5), paramProvider.apply(6));
                            return null;
                        };
                    case 9:
                        return (target, paramProvider) -> {
                            ((_Consumer9) invokeFunction).accept(
                                    target, paramProvider.apply(0), paramProvider.apply(1), paramProvider.apply(2), paramProvider.apply(3), paramProvider.apply(4), paramProvider.apply(5), paramProvider.apply(6), paramProvider.apply(7));
                            return null;
                        };
                    case 10:
                        return (target, paramProvider) -> {
                            ((_Consumer10) invokeFunction).accept(
                                    target, paramProvider.apply(0), paramProvider.apply(1), paramProvider.apply(2), paramProvider.apply(3), paramProvider.apply(4), paramProvider.apply(5), paramProvider.apply(6), paramProvider.apply(7),
                                    paramProvider.apply(8));
                            return null;
                        };
                    default:
                        throw new UnsupportedOperationException();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to create lambda type method invoke", e);
            return null;
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

    private FallbackFunction computeFallbackFunction() {
        if (member instanceof Method) {
            Method method = (Method) member;
            return (target, paramCount, paramSupplier) -> method.invoke(target, asArray(paramCount, paramSupplier));
        }
        if (member instanceof Field) {
            Field field = (Field) member;
            return (target, paramCount, paramSupplier) -> {
                if (paramCount == 0) {
                    return field.get(target);
                } else {
                    field.set(target, paramSupplier.apply(0));
                    return target;
                }
            };
        }
        if (member instanceof Constructor) {
            Constructor<?> constructor = (Constructor<?>) member;
            return (target, paramCount, paramSupplier) -> constructor.newInstance(asArray(paramCount, paramSupplier));
        }
        throw new UnsupportedOperationException("Member type not supported: " + member.getClass());
    }

    private Object[] asArray(int paramCount, IntFunction<?> paramSupplier) {
        var result = new Object[paramCount];
        for (int i = 0; i < paramCount; i++) {
            result[i] = paramSupplier.apply(i);
        }
        return result;
    }

    @FunctionalInterface
    private interface FallbackFunction {
        Object apply(Object target, int parameterCount, IntFunction<?> paramProvider) throws Throwable;
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

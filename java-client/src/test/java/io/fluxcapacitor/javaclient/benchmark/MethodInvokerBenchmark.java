package io.fluxcapacitor.javaclient.benchmark;

import io.fluxcapacitor.common.TimingUtils;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.reflection.DefaultMemberInvoker;
import io.fluxcapacitor.common.reflection.MemberInvoker;
import lombok.SneakyThrows;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static io.fluxcapacitor.common.handling.HandlerInspector.createHandler;
import static java.util.function.Function.identity;

class MethodInvokerBenchmark {
    private static final long iterations = 100_000_000L;
    private static final int WARM_UP = 2;

    public static void main(String[] args) throws Throwable {
        Person target = new Person("Ann");
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Method method = Person.class.getDeclaredMethod("name");
        MethodHandle realMethodHandle = lookup.unreflect(method);
        MemberInvoker invoker = DefaultMemberInvoker.asInvoker(method);
        Handler<Object> fluxHandler = createHandler(target, Handle.class, List.of(
                (parameter, methodAnnotation) -> identity()));
        HandlerInvoker<Object> fluxInvoker = fluxHandler.getInvoker(null);

        System.out.println("Invocation result of lambda: " + invoker.invoke(target));

        System.out.println("warming up");

        for (int i = 0; i < WARM_UP; i++) {
            testDirect(target);
            testInvoker(invoker, target);
            testMessageHandle(realMethodHandle, target);
            testReflection(method, target);
            testFluxInvoker(fluxInvoker, target);
            testFluxHandler(fluxHandler);
        }

        System.out.println("starting");

        TimingUtils.time(() -> testDirect(target), ms -> System.out.printf("direct: %dms, ", ms));
        TimingUtils.time(() -> testReflection(method, target), ms -> System.out.printf("reflection: %dms, ", ms));
        TimingUtils.time(() -> testMessageHandle(realMethodHandle, target), ms -> System.out.printf("method handle: %dms, ", ms));
        TimingUtils.time(() -> testInvoker(invoker, target), ms -> System.out.printf("lambda invoker: %dms, ", ms));
        TimingUtils.time(() -> testFluxInvoker(fluxInvoker, target),
                         elapsed -> System.out.printf("flux invoker: %dms, ", elapsed), ChronoUnit.MILLIS);
        TimingUtils.time(() -> testFluxHandler(fluxHandler), ms -> System.out.printf("flux handler: %dms, ", ms));

    }

    private static void testFluxInvoker(HandlerInvoker<Object> invoker, Person target) {
        for (long i = 0; i < iterations; i++) {
            invoker.invoke(target, null);
        }
    }

    private static void testDirect(Person target) {
        for (long i = 0; i < iterations; i++) {
            target.name();
        }
    }

    private static void testInvoker(MemberInvoker invoker, Person target) {
        for (long i = 0; i < iterations; i++) {
            invoker.invoke(target);
        }
    }

    private static void testFluxHandler(Handler<Object> handler) {
        for (long i = 0; i < iterations; i++) {
            handler.invoke(null);
        }
    }

    @SneakyThrows
    private static void testReflection(Method method, Person target) {
        for (long i = 0; i < iterations; i++) {
            method.invoke(target);
        }
    }

    @SneakyThrows
    private static void testMessageHandle(MethodHandle mh, Person target) {
        for (long i = 0; i < iterations; i++) {
            String result = (String) mh.invokeExact(target);
        }
    }

    static class Person {
        private long i = 0;
        private final String name;

        public Person(String name) {
            this.name = name;
        }

        @Handle
        private String name() {
            i++;
            return name;
        }
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Handle {
    }
}

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

import static io.fluxcapacitor.common.handling.HandlerInspector.createHandler;

class MethodInvokerBenchmark {
    private static final long iterations = 100_000_000L;
    private static final int WARM_UP = 2;

    public static void main(String[] args) throws Throwable {
        Person target = new Person("Ann");
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Method method = Person.class.getDeclaredMethod("name");
        MethodHandle realMethodHandle = lookup.unreflect(method);
        MemberInvoker invoker = DefaultMemberInvoker.asInvoker(method);
        Handler<Object> fluxHandler = createHandler(target, Handle.class);
        HandlerInvoker fluxInvoker = fluxHandler.getInvoker(null).orElseThrow();

        System.out.println("Invocation result of lambda: " + invoker.invoke(target));

        System.out.println("warming up");

        for (int i = 0; i < WARM_UP; i++) {
            testDirect(target);
            testInvoker(invoker, target);
            testMessageHandle(realMethodHandle, target);
            testReflection(method, target);
            testFluxInvoker(fluxInvoker);
            testFluxHandler(fluxHandler);
        }

        System.out.println("starting");

        TimingUtils.time(() -> testDirect(target), ms -> System.out.printf("direct: %dms, ", ms));
        TimingUtils.time(() -> testReflection(method, target), ms -> System.out.printf("reflection: %dms, ", ms));
        TimingUtils.time(() -> testMessageHandle(realMethodHandle, target), ms -> System.out.printf("method handle: %dms, ", ms));
        TimingUtils.time(() -> testInvoker(invoker, target), ms -> System.out.printf("lambda invoker: %dms, ", ms));
        TimingUtils.time(() -> testFluxInvoker(fluxInvoker),
                         elapsed -> System.out.printf("flux invoker: %dms, ", elapsed), ChronoUnit.MILLIS);
        TimingUtils.time(() -> testFluxHandler(fluxHandler), ms -> System.out.printf("flux handler: %dms, ", ms));

    }

    private static void testFluxInvoker(HandlerInvoker invoker) {
        for (long i = 0; i < iterations; i++) {
            invoker.invoke();
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
            handler.getInvoker(null).orElseThrow().invoke();
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

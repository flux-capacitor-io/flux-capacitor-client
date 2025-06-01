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

package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import lombok.NonNull;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * A {@link ParameterResolver} implementation that injects registered bean instances into method parameters
 * annotated with {@code @Autowired}.
 * <p>
 * This resolver allows basic dependency injection during test execution, emulating Spring-style autowiring.
 * <p>
 * Beans can be registered via {@link #registerBean(Object)}, and any parameter in a handler or fixture method
 * annotated with {@code @Autowired} (if present on the classpath) will be matched by type and resolved.
 * <p>
 * This class is especially useful in test scenarios (e.g., {@link io.fluxcapacitor.javaclient.test.TestFixture})
 * where full Spring context initialization is not desired or needed.
 */
public class BeanParameterResolver implements ParameterResolver<Object> {

    /**
     * The resolved annotation class for {@code org.springframework.beans.factory.annotation.Autowired}, if present.
     * Otherwise, falls back to a no-op annotation to disable matching.
     */
    @SuppressWarnings({"unchecked"})
    static final Class<? extends Annotation> autowiredClass = (Class<? extends Annotation>) ReflectionUtils.classForName(
            "org.springframework.beans.factory.annotation.Autowired", NoOpAnnotation.class);

    private final Collection<Object> beans = ConcurrentHashMap.newKeySet();

    /**
     * Registers a new injectable bean.
     * <p>
     * The bean will be used to resolve any compatible parameters annotated with {@code @Autowired}.
     *
     * @param bean the object to register
     * @return a {@link Registration} that allows deregistration of the bean
     */
    public Registration registerBean(@NonNull Object bean) {
        beans.add(bean);
        return () -> beans.remove(bean.getClass());
    }

    /**
     * Resolves the value for a parameter annotated with {@code @Autowired}.
     *
     * @param parameter        the method parameter to resolve
     * @param methodAnnotation unused (may be null)
     * @return a unary operator that provides the matching bean, or throws if no match is found
     */
    @Override
    public UnaryOperator<Object> resolve(Parameter p, Annotation methodAnnotation) {
        return v -> beans.stream().filter(b -> p.getType().isAssignableFrom(b.getClass())).findFirst().orElseThrow(
                () -> new IllegalStateException("No qualifying bean of type '" + p.getType() + "' available"));
    }

    /**
     * Determines whether this resolver supports resolving the given parameter.
     *
     * @param parameter        the method parameter to check
     * @param methodAnnotation unused (may be null)
     * @param value            unused (may be null)
     * @return {@code true} if the parameter is annotated with {@code @Autowired}, otherwise {@code false}
     */
    @Override
    public boolean matches(Parameter parameter, Annotation methodAnnotation, Object value) {
        return ReflectionUtils.has(autowiredClass, parameter);
    }

    /**
     * Dummy annotation used as fallback if {@code @Autowired} is not on the classpath.
     */
    @Target({})
    @Retention(RetentionPolicy.RUNTIME)
    @interface NoOpAnnotation {
    }
}

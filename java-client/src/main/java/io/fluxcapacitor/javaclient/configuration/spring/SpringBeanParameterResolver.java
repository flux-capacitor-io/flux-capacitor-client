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

package io.fluxcapacitor.javaclient.configuration.spring;

import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils.isQualifierMatch;

/**
 * Resolves handler method parameters annotated with {@link Autowired} from the Spring application context.
 * <p>
 * This resolver allows dependency injection of Spring-managed beans directly into handler methods. It supports both
 * type-based and {@link Qualifier}-based resolution, and will prioritize beans marked as {@code @Primary}.
 * <p>
 * If no bean can be resolved unambiguously (e.g., multiple candidates and no qualifier or primary), the parameter is
 * not injected and a warning is logged.
 *
 * <p>Example:
 * <pre>{@code
 * @HandleCommand
 * public void handle(MyCommand command, @Autowired MyService myService) {
 *     myService.performAction();
 * }
 * }</pre>
 */
@RequiredArgsConstructor
@Slf4j
public class SpringBeanParameterResolver implements ParameterResolver<Object> {
    private final Function<Parameter, UnaryOperator<Object>> resolverFunction = memoize(this::computeParameterResolver);

    private final ApplicationContext applicationContext;

    @Override
    public UnaryOperator<Object> resolve(Parameter p, Annotation methodAnnotation) {
        return resolverFunction.apply(p);
    }

    @Override
    public boolean matches(Parameter parameter, Annotation methodAnnotation, Object value) {
        return ReflectionUtils.has(Autowired.class, parameter);
    }

    protected UnaryOperator<Object> computeParameterResolver(Parameter p) {
        String[] beanNames = applicationContext.getBeanNamesForType(p.getType());
        return switch (beanNames.length) {
            case 0 -> throw new NoSuchBeanDefinitionException(p.getType());
            case 1 -> {
                String beanName = beanNames[0];
                yield v -> applicationContext.getAutowireCapableBeanFactory().getBean(beanName);
            }
            default -> {
                if (applicationContext.getAutowireCapableBeanFactory() instanceof ConfigurableListableBeanFactory f) {
                    Qualifier qualifier = p.getAnnotation(Qualifier.class);
                    if (qualifier != null) {
                        for (String beanName : beanNames) {
                            if (isQualifierMatch(qualifier.value()::equals, beanName, f)) {
                                yield v -> f.getBean(beanName);
                            }
                        }
                    }
                    for (String beanName : beanNames) {
                        if (f.containsBeanDefinition(beanName) && f.getBeanDefinition(beanName).isPrimary()) {
                            yield v -> f.getBean(beanName);
                        }
                    }
                }
                log.warn("{} beans of type {} were detected. However, none of them were designated as primary, "
                         + "and the parameter lacks @Qualifier. Consequently, this parameter will not be injected.",
                         beanNames.length, p.getType().getSimpleName());
                yield v -> null;
            }
        };
    }

}

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils.isQualifierMatch;

@RequiredArgsConstructor
@Slf4j
public class SpringBeanParameterResolver implements ParameterResolver<Object> {
    private static final List<String> annotationNames = List.of("Inject", "Autowired");
    private final Function<Parameter, UnaryOperator<Object>> resolverFunction = memoize(this::computeParameterResolver);

    private final ApplicationContext applicationContext;

    @Override
    public UnaryOperator<Object> resolve(Parameter p, Annotation methodAnnotation) {
        return resolverFunction.apply(p);
    }

    @Override
    public boolean matches(Parameter parameter, Annotation methodAnnotation, Object value, Object target) {
        return Arrays.stream(parameter.getAnnotations())
                       .anyMatch(a -> annotationNames.contains(a.annotationType().getSimpleName()))
               && resolverFunction.apply(parameter) != null;
    }

    protected UnaryOperator<Object> computeParameterResolver(Parameter p) {
        String[] beanNames = applicationContext.getBeanNamesForType(p.getType());
        return switch (beanNames.length) {
            case 0 -> null;
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
                yield null;
            }
        };
    }

}

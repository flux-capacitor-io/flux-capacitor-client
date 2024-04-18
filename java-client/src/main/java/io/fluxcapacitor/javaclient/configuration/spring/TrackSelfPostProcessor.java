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

import io.fluxcapacitor.javaclient.tracking.TrackSelf;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ScannedGenericBeanDefinition;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Stream.concat;
import static org.springframework.beans.factory.support.BeanDefinitionBuilder.genericBeanDefinition;

@Slf4j
public class TrackSelfPostProcessor implements BeanDefinitionRegistryPostProcessor, EnvironmentAware {
    private Environment environment;

    public TrackSelfPostProcessor() {

    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            log.warn("Cannot register Spring beans dynamically! @TrackSelf annotations will be ignored.");
            return;
        }

        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false, environment) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isIndependent();
            }
        };
        provider.addIncludeFilter(new AnnotationTypeFilter(TrackSelf.class));

        Arrays.stream(beanFactory.getBeanNamesForAnnotation(ComponentScan.class)).map(beanFactory::getType)
                .filter(Objects::nonNull).flatMap(c -> {
                    var basePackages = AnnotatedElementUtils.getMergedRepeatableAnnotations(c, ComponentScan.class)
                            .stream().flatMap(scan -> concat(Arrays.stream(scan.basePackages()),
                                                             Arrays.stream(scan.basePackageClasses())
                                                                     .map(Class::getPackageName)))
                            .distinct().toList();
                    if (basePackages.isEmpty()) {
                        return Stream.of(c.getPackageName());
                    }
                    return basePackages.stream();
                }).flatMap(p -> {
                    Set<BeanDefinition> candidateComponents = provider.findCandidateComponents(p);
                    return candidateComponents.stream();
                }).map(this::extractBeanClass).filter(Objects::nonNull).distinct()
                .forEach(type -> {
                    var prototype = new FluxPrototype(type);
                    registry.registerBeanDefinition(
                            type.getName() + "$$SelfTracked",
                            genericBeanDefinition(FluxPrototype.class, () -> prototype).getBeanDefinition());
                });
    }

    protected Class<?> extractBeanClass(BeanDefinition beanDefinition) {
        try {
            return ((ScannedGenericBeanDefinition) beanDefinition)
                    .resolveBeanClass(Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry) throws BeansException {
        //no op
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
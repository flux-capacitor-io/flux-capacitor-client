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

import io.fluxcapacitor.javaclient.tracking.handling.View;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;

import java.util.Arrays;
import java.util.Objects;

import static org.springframework.beans.factory.support.BeanDefinitionBuilder.genericBeanDefinition;

@Slf4j
public class ViewPostProcessor implements BeanDefinitionRegistryPostProcessor {
    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
            log.warn("Cannot register Spring beans dynamically! @View annotations will be ignored.");
            return;
        }
        Arrays.stream(beanFactory.getBeanNamesForAnnotation(View.class))
                .map(beanFactory::getType).filter(Objects::nonNull)
                .map(FluxPrototype::new)
                .forEach(prototype -> registry.registerBeanDefinition(
                         prototype.getType().getName()+ "$$View",
                         genericBeanDefinition(FluxPrototype.class, () -> prototype).getBeanDefinition()));
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry) throws BeansException {
        //no op
    }
}
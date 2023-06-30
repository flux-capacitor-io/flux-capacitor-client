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

import lombok.SneakyThrows;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.MethodMetadata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.springframework.util.ClassUtils.forName;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(ConditionalOnMissingBean.Condition.class)
public @interface ConditionalOnMissingBean {

    Class<?> value() default void.class;

    @SuppressWarnings({"NullableProblems", "ConstantConditions"})
    @Order
    class Condition implements org.springframework.context.annotation.Condition {
        @Override
        @SneakyThrows
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            if (metadata instanceof MethodMetadata) {
                Class<?> type = (Class<?>) metadata.getAllAnnotationAttributes(
                        ConditionalOnMissingBean.class.getName()).getFirst("value");
                if (void.class.equals(type)) {
                    type = forName(((MethodMetadata) metadata).getReturnTypeName(), context.getClassLoader());
                }
                return BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context.getBeanFactory(), type).length == 0;
            }
            Class<?> type = (Class<?>) metadata.getAllAnnotationAttributes(ConditionalOnMissingBean.class.getName())
                    .getFirst("value");
            if (void.class.equals(type)) {
                type = forName(metadata.getAnnotations().get(ConditionalOnMissingBean.class).getSource().toString(),
                               context.getClassLoader());
            }
            String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context.getBeanFactory(), type);
            return beanNames.length == 0;
        }
    }

}

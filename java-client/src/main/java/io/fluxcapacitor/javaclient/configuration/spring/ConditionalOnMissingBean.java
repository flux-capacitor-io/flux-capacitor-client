/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.ClassUtils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Conditional(ConditionalOnMissingBean.Condition.class)
public @interface ConditionalOnMissingBean {

    class Condition implements org.springframework.context.annotation.Condition {
        @Override
        @SneakyThrows
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Class<?> type =
                    ClassUtils.forName(((MethodMetadata) metadata).getReturnTypeName(), context.getClassLoader());
            return BeanFactoryUtils.beanNamesForTypeIncludingAncestors(context.getBeanFactory(), type).length == 0;
        }
    }

}

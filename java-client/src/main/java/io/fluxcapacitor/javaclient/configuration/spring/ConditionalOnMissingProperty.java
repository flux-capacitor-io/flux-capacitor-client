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

import io.fluxcapacitor.javaclient.configuration.ApplicationProperties;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link Conditional} that only matches when the specified property is either unset or blank.
 * <p>
 * This can be used to register default behavior when configuration is absent.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @ConditionalOnMissingProperty("custom.datasource.url")
 * @Bean
 * public DataSource defaultDataSource() {
 *     return new H2DataSource();
 * }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(ConditionalOnMissingProperty.Condition.class)
public @interface ConditionalOnMissingProperty {

    String value();

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @SuppressWarnings("ConstantConditions")
    class Condition implements org.springframework.context.annotation.Condition {
        @Override
        @SneakyThrows
        public boolean matches(@NotNull ConditionContext context, AnnotatedTypeMetadata metadata) {
            String propertyName = metadata.getAllAnnotationAttributes(ConditionalOnMissingProperty.class.getName())
                    .getFirst("value").toString();
            String value = ApplicationProperties.getProperty(propertyName);
            if (value == null) {
                value = context.getEnvironment().getProperty(propertyName);
            }
            return value == null || value.isEmpty();
        }
    }

}

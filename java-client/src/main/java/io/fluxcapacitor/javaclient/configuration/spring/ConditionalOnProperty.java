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
 * {@link Conditional} that only matches when a specific property is set and matches a given pattern.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @ConditionalOnProperty(value = "feature.enabled")
 * @Bean
 * public FeatureService featureService() {
 *     return new FeatureService();
 * }
 * }</pre>
 *
 * <p>
 * The pattern is a regular expression (defaults to {@code ".+"}, i.e., non-empty).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(ConditionalOnProperty.Condition.class)
public @interface ConditionalOnProperty {

    String value();

    String pattern() default ".+";

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @SuppressWarnings("ConstantConditions")
    class Condition implements org.springframework.context.annotation.Condition {
        @Override
        @SneakyThrows
        public boolean matches(@NotNull ConditionContext context, AnnotatedTypeMetadata metadata) {
            String value = ApplicationProperties.getProperty(
                    metadata.getAllAnnotationAttributes(ConditionalOnProperty.class.getName()).getFirst("value")
                            .toString());
            String pattern =
                    metadata.getAllAnnotationAttributes(ConditionalOnProperty.class.getName()).getFirst("pattern")
                            .toString();
            return value != null && value.matches(pattern);
        }
    }

}

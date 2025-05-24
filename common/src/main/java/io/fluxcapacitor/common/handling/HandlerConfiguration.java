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

package io.fluxcapacitor.common.handling;

import io.fluxcapacitor.common.reflection.ReflectionUtils;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Accessors;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

import static java.util.Optional.ofNullable;

/**
 * Configuration object used to define how message handler methods are selected and filtered for a given message type.
 * <p>
 * This configuration is passed to {@link HandlerInspector} and {@link HandlerMatcher} instances to determine:
 * <ul>
 *   <li>Which methods should be considered as valid message handlers,</li>
 *   <li>Whether multiple methods on the same target are allowed to handle the same message,</li>
 *   <li>And whether a particular message is eligible for handling by a given method.</li>
 * </ul>
 *
 * <p>The generic type {@code M} represents the expected message wrapper type (e.g. {@code HasMessage} or a subclass).
 *
 * @param <M> the message wrapper type this configuration applies to
 */
@Value
@Builder(toBuilder = true)
@Accessors(fluent = true)
public class HandlerConfiguration<M> {

    /**
     * The annotation type that marks a method as a handler (e.g. {@code @HandleCommand}, {@code @HandleQuery}).
     * If {@code null}, all methods are considered eligible for inspection.
     */
    Class<? extends Annotation> methodAnnotation;

    /**
     * If {@code true}, allows multiple methods in the same class to handle a single message.
     * <p>
     * If {@code false}, only the first matching handler (by inspection order) will be invoked.
     */
    @Default
    boolean invokeMultipleMethods = false;

    /**
     * A filter applied to candidate methods to determine if they are valid handlers for the given context.
     * <p>
     * The filter receives the owning class and the method as inputs. By default, all methods are accepted.
     */
    @Default
    HandlerFilter handlerFilter = (c, e) -> true;

    /**
     * A filter applied at runtime to check if a given message should be routed to the specified method.
     * <p>
     * This is used after the handler method has been identified and may use logic such as payload class matching,
     * topic filtering, or segment-based routing.
     */
    @Default
    MessageFilter<? super M> messageFilter = (m, e, handlerAnnotation) -> true;

    /**
     * Determines whether the given method is eligible to handle messages according to this configuration.
     * <p>
     * This includes both:
     * <ul>
     *   <li>Checking that the method is annotated with {@link #methodAnnotation} (if applicable), and</li>
     *   <li>That it passes the {@link #handlerFilter}.</li>
     * </ul>
     *
     * @param c the class owning the method
     * @param e the method to check
     * @return {@code true} if the method should be included as a handler
     */
    public boolean methodMatches(Class<?> c, Executable e) {
        return isEnabled(e) && handlerFilter.test(c, e);
    }

    /**
     * Determines whether the given method is "enabled" based on the presence of {@link #methodAnnotation}
     * and whether the annotation declares a {@code disabled()} attribute.
     * <p>
     * If the annotation exists and its {@code disabled()} attribute evaluates to {@code true}, the method is skipped.
     *
     * @param e the method to evaluate
     * @return {@code true} if the method is enabled and can be considered as a handler
     */
    @SneakyThrows
    boolean isEnabled(Executable e) {
        if (methodAnnotation == null) {
            return true;
        }
        var annotation = getAnnotation(e).orElse(null);
        if (annotation == null) {
            return false;
        }
        Optional<Method> match = Arrays.stream(annotation.annotationType().getMethods())
                .filter(m -> m.getName().equals("disabled")).findFirst();
        if (match.isPresent()) {
            var result = (boolean) match.get().invoke(annotation);
            return !result;
        }
        return true;
    }

    /**
     * Retrieves the configured method annotation on the given method, if present.
     *
     * @param e the method to inspect
     * @return an optional containing the annotation if present, or empty otherwise
     */
    public Optional<? extends Annotation> getAnnotation(Executable e) {
        return ofNullable(methodAnnotation).flatMap(a -> ReflectionUtils.getMethodAnnotation(e, methodAnnotation));
    }
}

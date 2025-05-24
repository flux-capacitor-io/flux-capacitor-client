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

package io.fluxcapacitor.common.reflection;

import java.lang.reflect.Member;
import java.util.function.IntFunction;

/**
 * Abstraction for invoking a {@link Member} (e.g. {@link java.lang.reflect.Method},
 * {@link java.lang.reflect.Constructor}, or {@link java.lang.reflect.Field}) reflectively.
 * <p>
 * This interface provides a uniform API to invoke members on a target object with zero or more parameters.
 * <p>
 * See {@link DefaultMemberInvoker} for the main implementation which uses high-performance invocation strategies using
 * method handles and lambdas as opposed to traditional Java reflection resulting in near-native invocation performance.
 */
public interface MemberInvoker {

    /**
     * Invokes the member with no parameters.
     *
     * @param target the target object or null for static members
     * @return the result of the invocation (or null for void-returning methods)
     */
    default Object invoke(Object target) {
        return invoke(target, 0, i -> null);
    }

    /**
     * Invokes the member with a single parameter.
     *
     * @param target the target object
     * @param param  the single parameter
     * @return the invocation result
     */
    default Object invoke(Object target, Object param) {
        return invoke(target, 1, i -> param);
    }

    /**
     * Invokes the member with a variable number of parameters.
     *
     * @param target the target object or null for static members
     * @param params the parameters to pass
     * @return the result of the invocation
     */
    default Object invoke(Object target, Object... params) {
        return invoke(target, params.length, i -> params[i]);
    }

    /**
     * Invokes the member with the provided number of parameters using a parameter provider.
     *
     * @param target            the target object
     * @param parameterCount    the number of parameters to use
     * @param parameterProvider a function to supply parameters by index
     * @return the invocation result
     */
    Object invoke(Object target, int parameterCount, IntFunction<?> parameterProvider);

    /**
     * @return the underlying reflective member
     */
    Member getMember();
}

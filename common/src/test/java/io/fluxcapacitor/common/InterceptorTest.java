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

package io.fluxcapacitor.common;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;

public class InterceptorTest {

    @Test
    public void testInvocationOrder() {
        List<Object> invokedInstances = new ArrayList<>();
        Interceptor<String, Object> outerInterceptor = new Interceptor<String, Object>() {
            @Override
            public Function<String, Object> intercept(Function<String, Object> function) {
                return s -> {
                    invokedInstances.add(this);
                    return function.apply(s);
                };
            }
        };
        Interceptor<String, Object> innerInterceptor = new Interceptor<String, Object>() {
            @Override
            public Function<String, Object> intercept(Function<String, Object> function) {
                return s -> {
                    invokedInstances.add(this);
                    return function.apply(s);
                };
            }
        };
        Function<String, Object> function = new Function<String, Object>() {
            @Override
            public Object apply(String s) {
                return invokedInstances.add(this);
            }
        };
        Function<String, Object> invocation = Interceptor.join(Arrays.asList(outerInterceptor, innerInterceptor)).intercept(function);
        assertEquals(emptyList(), invokedInstances);
        invocation.apply("test");
        assertEquals(Arrays.asList(outerInterceptor, innerInterceptor, function), invokedInstances);
    }
}
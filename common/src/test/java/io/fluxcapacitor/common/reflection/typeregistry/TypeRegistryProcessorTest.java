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

package io.fluxcapacitor.common.reflection.typeregistry;

import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.common.reflection.typeregistry.bar.Bar;
import io.fluxcapacitor.common.serialization.TypeRegistryProcessor;
import org.joor.CompileOptions;
import org.joor.Reflect;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TypeRegistryProcessorTest {

    @Test
    void classForName() {
        assertThrows(ClassNotFoundException.class, () -> ReflectionUtils.classForName("Foo"));
        assertEquals(Foo.class, ReflectionUtils.classForName(Foo.class.getName()));
        assertEquals(Bar.class, ReflectionUtils.classForName("Bar"));
        assertEquals(FooBar.class, ReflectionUtils.classForName("FooBar"));
        assertEquals(FooBar.class, ReflectionUtils.classForName("typeregistry.FooBar"));
    }

    @Test
    @Disabled
    void testCompilation() {
        TypeRegistryProcessor p = new TypeRegistryProcessor();
        Reflect.compile(
                "io.fluxcapacitor.common.reflection.typeregistry.SomeHandler",
                """
                            package io.fluxcapacitor.common.reflection.test;
                            @io.fluxcapacitor.common.serialization.RegisterType
                            public class SomeHandler {
                            }
                        """, new CompileOptions().processors(p)
        );
    }

}
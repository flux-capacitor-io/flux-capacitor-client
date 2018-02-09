/*
 * Copyright (c) 2016-2018 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.common.reflection;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public class ReflectionUtils {

    public static Optional<?> getAnnotatedPropertyValue(Object target, Class<? extends Annotation> annotation) {
        if (target == null) {
            return Optional.empty();
        }
        Optional<?> result = FieldUtils.getFieldsListWithAnnotation(target.getClass(), annotation).stream().findFirst()
                .map(f -> getProperty(f, target));
        if (!result.isPresent()) {
            return MethodUtils.getMethodsListWithAnnotation(target.getClass(), annotation, true, true)
                    .stream().findFirst().map(m -> getProperty(m, target));
        }
        return result;
    }

    private static Object getProperty(Method method, Object target) {
        method.setAccessible(true);
        try {
            return method.invoke(target);
        } catch (Exception e) {
            throw new PropertyAccessException(method, e);
        }
    }

    private static Object getProperty(Field field, Object target) {
        field.setAccessible(true);
        try {
            return field.get(target);
        } catch (Exception e) {
            throw new PropertyAccessException(field, e);
        }
    }

}

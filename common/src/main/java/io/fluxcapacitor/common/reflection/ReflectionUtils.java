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

package io.fluxcapacitor.common.reflection;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.PrivilegedAction;
import java.util.Optional;

import static java.security.AccessController.doPrivileged;

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
        try {
            return ensureAccessible(method).invoke(target);
        } catch (Exception e) {
            throw new PropertyAccessException(method, e);
        }
    }

    private static Object getProperty(Field field, Object target) {
        try {
            return ensureAccessible(field).get(target);
        } catch (Exception e) {
            throw new PropertyAccessException(field, e);
        }
    }

    public static <T extends AccessibleObject> T ensureAccessible(T member) {
        if (!isAccessible(member)) {
            doPrivileged((PrivilegedAction<?>) () -> {
                member.setAccessible(true);
                return null;
            });
        }
        return member;
    }

    private static boolean isAccessible(AccessibleObject member) {
        return member.isAccessible() || (Member.class.isInstance(member) && isNonFinalPublicMember((Member) member));
    }

    private static boolean isNonFinalPublicMember(Member member) {
        return (Modifier.isPublic(member.getModifiers())
                && Modifier.isPublic(member.getDeclaringClass().getModifiers())
                && !Modifier.isFinal(member.getModifiers()));
    }

}

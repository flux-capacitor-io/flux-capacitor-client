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

import lombok.Value;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class GenericTypeResolver {

    public static Type getGenericType(Class<?> clazz, Class<?> target) {
        return resolveType(clazz, target, new HashMap<>());
    }

    private static Type resolveType(Class<?> clazz, Class<?> target, Map<TypeVariable<?>, Type> typeVarMap) {
        if (clazz == null || clazz == Object.class) return null;

        // Direct superclass
        Type genericSuperclass = clazz.getGenericSuperclass();
        Type resolved = matchAndResolve(genericSuperclass, target, typeVarMap);
        if (resolved != null) return resolved;

        // Interfaces
        for (Type genericInterface : clazz.getGenericInterfaces()) {
            resolved = matchAndResolve(genericInterface, target, typeVarMap);
            if (resolved != null) return resolved;
        }

        // Recurse upward
        return resolveType(clazz.getSuperclass(), target, typeVarMap);
    }

    private static Type matchAndResolve(Type candidate, Class<?> target, Map<TypeVariable<?>, Type> typeVarMap) {
        if (candidate instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) candidate;
            Class<?> rawType = (Class<?>) pt.getRawType();

            if (rawType.equals(target)) {
                return reifyType(pt, typeVarMap);
            }

            Map<TypeVariable<?>, Type> newMap = new HashMap<>(typeVarMap);
            TypeVariable<?>[] vars = rawType.getTypeParameters();
            Type[] args = pt.getActualTypeArguments();
            for (int i = 0; i < vars.length; i++) {
                newMap.put(vars[i], resolveTypeVariable(args[i], typeVarMap));
            }

            return resolveType(rawType, target, newMap);
        } else if (candidate instanceof Class) {
            Class<?> raw = (Class<?>) candidate;
            if (raw.equals(target)) {
                return raw;
            }
            return resolveType(raw, target, typeVarMap);
        }
        return null;
    }

    private static Type reifyType(Type type, Map<TypeVariable<?>, Type> typeVarMap) {
        if (type instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) type;
            Type raw = pt.getRawType();
            Type[] args = Arrays.stream(pt.getActualTypeArguments())
                    .map(t -> resolveTypeVariable(t, typeVarMap))
                    .toArray(Type[]::new);
            return new ParameterizedTypeImpl((Class<?>) raw, args, pt.getOwnerType());
        } else if (type instanceof GenericArrayType) {
            Type component = reifyType(((GenericArrayType) type).getGenericComponentType(), typeVarMap);
            return Array.newInstance((Class<?>) erase(component), 0).getClass();
        } else if (type instanceof TypeVariable<?>) {
            return resolveTypeVariable(type, typeVarMap);
        } else {
            return type;
        }
    }

    private static Type resolveTypeVariable(Type type, Map<TypeVariable<?>, Type> typeVarMap) {
        if (type instanceof TypeVariable<?>) {
            Type resolved = typeVarMap.get(type);
            if (resolved != null) {
                return resolveTypeVariable(resolved, typeVarMap);
            } else {
                // Use the bound or Object as fallback
                TypeVariable<?> var = (TypeVariable<?>) type;
                Type[] bounds = var.getBounds();
                return bounds.length > 0 ? resolveTypeVariable(bounds[0], typeVarMap) : Object.class;
            }
        } else if (type instanceof ParameterizedType) {
            return reifyType(type, typeVarMap);
        } else if (type instanceof GenericArrayType) {
            return reifyType(type, typeVarMap);
        } else {
            return type;
        }
    }

    private static Type erase(Type type) {
        if (type instanceof Class<?>) {
            return type;
        } else if (type instanceof ParameterizedType) {
            return ((ParameterizedType) type).getRawType();
        } else if (type instanceof GenericArrayType) {
            return Array.newInstance((Class<?>) erase(((GenericArrayType) type).getGenericComponentType()), 0).getClass();
        } else if (type instanceof TypeVariable<?>) {
            return Object.class;
        } else {
            throw new IllegalArgumentException("Cannot erase type: " + type);
        }
    }

    @Value
    private static class ParameterizedTypeImpl implements ParameterizedType {
        Class<?> rawType;
        Type[] actualTypeArguments;
        Type ownerType;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(rawType.getTypeName());
            if (actualTypeArguments.length > 0) {
                sb.append("<");
                for (int i = 0; i < actualTypeArguments.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(actualTypeArguments[i].getTypeName());
                }
                sb.append(">");
            }
            return sb.toString();
        }
    }
}

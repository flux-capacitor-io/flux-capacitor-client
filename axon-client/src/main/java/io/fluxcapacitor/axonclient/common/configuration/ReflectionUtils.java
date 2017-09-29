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

package io.fluxcapacitor.axonclient.common.configuration;

import java.lang.reflect.Field;

public class ReflectionUtils {

    @SuppressWarnings("unchecked")
    public static <T> T getField(String location, Object instance) {
        try {
            String[] paths = location.split("/");
            Object object = instance;
            for (String path : paths) {
                Field field = object.getClass().getDeclaredField(path);
                field.setAccessible(true);
                object = field.get(object);
            }
            return (T) object;
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Could not find %s on instance %s", location, instance));
        }
    }

}

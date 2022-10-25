/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.persisting.keyvalue;

import io.fluxcapacitor.common.Guarantee;

public interface KeyValueStore {

    default void store(String key, Object value) {
        store(key, value, Guarantee.SENT);
    }

    void store(String key, Object value, Guarantee guarantee);

    void storeIfAbsent(String key, Object value);

    <R> R get(String key);

    void delete(String key);

}

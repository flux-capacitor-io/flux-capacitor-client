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

package io.fluxcapacitor.javaclient.persisting.caching;

import java.util.function.Function;

public enum NoOpCache implements Cache {
    INSTANCE;

    @Override
    public void put(String id, Object value) {
        //no op
    }

    @Override
    public <T> T get(String id, Function<? super String, T> mappingFunction) {
        return mappingFunction.apply(id);
    }

    @Override
    public <T> T getIfPresent(String id) {
        return null;
    }

    @Override
    public void invalidate(String id) {
        //no op
    }

    @Override
    public void invalidateAll() {
        //no op
    }
}

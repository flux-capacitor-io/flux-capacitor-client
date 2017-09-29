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

package io.fluxcapacitor.common.handling;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CompositeHandlerInvoker<M> implements HandlerInvoker<M> {
    private final List<HandlerInvoker<M>> delegates;

    public CompositeHandlerInvoker(List<? extends HandlerInvoker<M>> delegates) {
        this.delegates = new ArrayList<>(delegates);
    }

    @Override
    public boolean canHandle(M message) {
        return delegates.stream().anyMatch(h -> h.canHandle(message));
    }

    @Override
    public Object invoke(Object target, M message) throws Exception {
        Optional<HandlerInvoker<M>> delegate = delegates.stream().filter(d -> d.canHandle(message)).findFirst();
        if (!delegate.isPresent()) {
            throw new IllegalArgumentException("No delegate found that could handle " + message);
        }
        return delegate.get().invoke(target, message);
    }
}

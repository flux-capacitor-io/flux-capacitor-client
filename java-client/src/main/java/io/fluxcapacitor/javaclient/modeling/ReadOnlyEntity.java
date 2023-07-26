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

package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.common.Message;
import lombok.Value;

import java.util.function.UnaryOperator;

@Value
public class ReadOnlyEntity<T> extends DelegatingEntity<T> {
    public ReadOnlyEntity(Entity<T> delegate) {
        super(delegate);
    }

    @Override
    public Entity<T> apply(Message eventMessage) {
        throw new UnsupportedOperationException("This aggregate is read-only");
    }

    @Override
    public Entity<T> update(UnaryOperator<T> function) {
        throw new UnsupportedOperationException("This aggregate is read-only");
    }

    @Override
    public <E extends Exception> Entity<T> assertLegal(Object command) throws E {
        return this;
    }
}
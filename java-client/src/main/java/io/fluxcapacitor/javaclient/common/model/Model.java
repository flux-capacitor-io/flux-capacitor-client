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

package io.fluxcapacitor.javaclient.common.model;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;

import java.util.function.Function;
import java.util.function.Predicate;

public interface Model<T> {

    default Model<T> apply(Object event) {
        return apply(new Message(event, MessageType.EVENT));
    }

    default Model<T> apply(Object event, Metadata metadata) {
        return apply(new Message(event, metadata, MessageType.EVENT));
    }

    Model<T> apply(Message message);

    default Model<T> apply(Function<T, Message> eventFunction) {
        return apply(eventFunction.apply(get()));
    }

    default <E extends Exception> Model<T> ensure(Predicate<T> check, Function<T, E> errorProvider) throws E {
        if (!check.test(get())) {
            throw errorProvider.apply(get());
        }
        return this;
    }

    T get();

    long getSequenceNumber();
}

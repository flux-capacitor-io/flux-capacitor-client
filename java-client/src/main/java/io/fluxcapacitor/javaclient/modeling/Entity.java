/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.common.Message.asMessage;

public interface Entity<M extends Entity<M, T>, T> {

    Object id();

    Class<T> type();

    T get();

    String idProperty();

    Collection<? extends Entity<?, ?>> entities();

    default Stream<Entity<?, ?>> allEntities() {
        return Stream.concat(Stream.of(this), entities().stream().flatMap(Entity::allEntities));
    }

    default Optional<Entity<?, ?>> getEntity(Object entityId) {
        return allEntities().filter(e -> entityId.equals(e.id())).findFirst();
    }

    default M apply(Object... events) {
        return apply(List.of(events));
    }

    @SuppressWarnings("unchecked")
    default M apply(Collection<?> events) {
        M result = (M) this;
        for (Object event : events) {
            result = apply(event);
        }
        return result;
    }

    default M apply(Object event) {
        if (event instanceof DeserializingMessage) {
            return apply(((DeserializingMessage) event).toMessage());
        }
        return apply(asMessage(event));
    }

    default M apply(Object event, Metadata metadata) {
        return apply(new Message(event, metadata));
    }

    M apply(Message eventMessage);

    M update(UnaryOperator<T> function);

    default <E extends Exception> M assertLegal(Object command) throws E {
        if (command instanceof Collection<?>) {
            return assertLegal(((Collection<?>) command).toArray());
        }
        return assertLegal(new Object[]{command});
    }

    <E extends Exception> M assertLegal(Object... commands) throws E;

    @SuppressWarnings("unchecked")
    default <E extends Exception> M assertThat(Validator<T, E> validator) throws E {
        validator.validate(this.get());
        return (M) this;
    }

    @SuppressWarnings("unchecked")
    default <E extends Exception> M ensure(Predicate<T> check, Function<T, E> errorProvider) throws E {
        if (!check.test(get())) {
            throw errorProvider.apply(get());
        }
        return (M) this;
    }

    @FunctionalInterface
    interface Validator<T, E extends Exception> {
        void validate(T model) throws E;
    }
}
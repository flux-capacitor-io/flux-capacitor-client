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
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils;

import java.beans.Transient;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static io.fluxcapacitor.javaclient.common.Message.asMessage;

public interface Entity<M extends Entity<M, T>, T> {

    String id();

    Class<T> type();

    T get();

    @Transient
    Map<String, ? extends Entity<?, ?>> entities();

    @SuppressWarnings("unchecked")
    default <E> Optional<Entity<?, E>> getEntity(String id) {
        return Optional.ofNullable((Entity<?, E>) entities().get(id));
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

    @SuppressWarnings("unchecked")
    default <E extends Exception> M assertLegal(Object command) throws E {
        if (command instanceof Collection<?>) {
            return assertLegal(((Collection<?>) command).toArray());
        }
        ValidationUtils.assertLegal(command, this);
        return (M) this;
    }

    @SuppressWarnings("unchecked")
    default <E extends Exception> M assertLegal(Object... commands) throws E {
        switch (commands.length) {
            case 0:
                return (M) this;
            case 1:
                ValidationUtils.assertLegal(commands[0], this);
                return (M) this;
            default:
                M result = (M) this;
                Iterator<Object> iterator = Arrays.stream(commands).iterator();
                while (iterator.hasNext()) {
                    Object c = iterator.next();
                    ValidationUtils.assertLegal(c, result);
                    if (iterator.hasNext()) {
                        result = result.apply(Message.asMessage(c));
                    }
                }
                return (M) this;
        }
    }

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
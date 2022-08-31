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

package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.persisting.search.Search;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.web.WebRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public interface When {

    Then whenCommand(Object command);

    Then whenCommandByUser(Object command, User user);

    Then whenQuery(Object query);

    Then whenQueryByUser(Object command, User user);

    Then whenEvent(Object event);

    Then whenEventsAreApplied(String aggregateId, Object... events);

    Then whenSearching(String collection, UnaryOperator<Search> searchQuery);

    default Then whenSearching(String collection, Constraint... constraints) {
        return whenSearching(collection, s -> s.constraint(constraints));
    }

    Then whenWebRequest(WebRequest request);

    Then whenScheduleExpires(Object schedule);

    Then whenTimeAdvancesTo(Instant instant);

    Then whenTimeElapses(Duration duration);

    default Then whenExecuting(Consumer<FluxCapacitor> action) {
        return whenApplying(fc -> {
            action.accept(fc);
            return null;
        });
    }

    Then whenApplying(Function<FluxCapacitor, ?> action);
}

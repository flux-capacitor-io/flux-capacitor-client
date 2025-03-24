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

package io.fluxcapacitor.javaclient.persisting.search;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.modeling.EntityId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;
import static io.fluxcapacitor.javaclient.FluxCapacitor.currentIdentityProvider;
import static io.fluxcapacitor.javaclient.common.ClientUtils.getSearchParameters;
import static io.fluxcapacitor.javaclient.modeling.SearchParameters.defaultSearchParameters;
import static java.util.Optional.ofNullable;

@Data
@Accessors(chain = true, fluent = true)
@AllArgsConstructor
public class DefaultIndexOperation implements IndexOperation {

    final transient DocumentStore documentStore;
    final Object value;

    public DefaultIndexOperation(DocumentStore documentStore, @NonNull Object object) {
        var searchParams = ofNullable(getSearchParameters(object.getClass())).orElse(defaultSearchParameters);
        this.documentStore = documentStore;
        this.value = object;
        collection = ofNullable(searchParams.getCollection()).orElseGet(object.getClass()::getSimpleName);
        start = ReflectionUtils.<Instant>readProperty(searchParams.getTimestampPath(), object).orElse(null);
        end = ReflectionUtils.hasProperty(searchParams.getEndPath(), object)
                ? ReflectionUtils.<Instant>readProperty(searchParams.getEndPath(), object).orElse(null) : start;
        id = getAnnotatedPropertyValue(object, EntityId.class).map(Object::toString)
                .orElseGet(() -> currentIdentityProvider().nextTechnicalId());
    }

    @NonNull Object collection;
    @NonNull Object id;
    @NonNull Metadata metadata = Metadata.empty();
    Instant start, end;
    boolean ifNotExists;

    @Override
    public CompletableFuture<Void> index(Guarantee guarantee) {
        return documentStore.index(value, id, collection, start, end, metadata, guarantee, ifNotExists);
    }

    @Override
    public IndexOperation copy() {
        return new DefaultIndexOperation(documentStore, value, collection, id, metadata, start, end, ifNotExists);
    }
}

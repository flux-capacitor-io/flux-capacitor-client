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

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fluxcapacitor.common.search.Facet;
import lombok.Getter;
import lombok.NonNull;

import java.util.Objects;

/**
 * Object that represents the identifier of a specific entity. This object makes it easy to prefix the functional id of
 * an entity to with a value before storing or lookup in a repository to prevent clashes with other entities that may
 * share the same functional id.
 * <p>
 * Additionally, this object makes it possible to store and lookup entities using a case-insensitive identifier.
 * <p>
 * It also allows specifying the entity type which prevents the need for dynamic casting after loading the entity.
 * <p>
 * Note that, by default, this identifier is serialized as a string of the functionalId. Deserialization is done by
 * invoking a constructor on your subtype that accepts a single String argument. If such constructor does not exist,
 * please specify your own deserializer, using e.g. {@link JsonDeserialize @JsonDeserialize} on your type.
 *
 * @param <T> the entity type.
 */
public abstract class Id<T> {
    @JsonValue
    @Getter
    @Facet
    String functionalId;
    @Getter
    Class<T> type;
    String repositoryId;

    /**
     * Construct a case-sensitive id for an entity without prefix. This constructor allows ids to be prefixed with a
     * value to prevent clashes with other entities that may share the same functional id.
     *
     * @param functionalId The functional id of the entity. The object's toString() method is used to get a string
     *                     representation of the functional id.
     * @param type         The entity's type. This may be a superclass of the actual entity.
     */
    protected Id(Object functionalId, Class<T> type) {
        this(functionalId, type, "");
    }

    /**
     * Construct a case-sensitive id for an entity. This constructor allows ids to be prefixed with a value to prevent
     * clashes with other entities that may share the same functional id.
     *
     * @param functionalId The functional id of the entity. The object's toString() method is used to get a string
     *                     representation of the functional id.
     * @param type         The entity's type. This may be a superclass of the actual entity.
     * @param prefix       The prefix that is prepended to the {@link #functionalId} to create the full id under which
     *                     this entity will be stored. Eg, if the prefix of an {@link Id} is "user-", and the id is
     *                     "pete123", the entity will be stored under "user-pete123".
     */
    protected Id(Object functionalId, Class<T> type, String prefix) {
        this(functionalId, type, prefix, true);
    }

    /**
     * Construct an id for an entity without prefix. This constructor allows ids to be case-insensitive.
     *
     * @param functionalId  The functional id of the entity. The object's toString() method is used to get a string
     *                      representation of the functional id.
     * @param type          The entity's type. This may be a superclass of the actual entity.
     * @param caseSensitive whether this id is case-sensitive.
     */
    protected Id(Object functionalId, Class<T> type, boolean caseSensitive) {
        this(functionalId, type, "", caseSensitive);
    }

    /**
     * Construct an id for an entity. This constructor allows ids to be prefixed with a value to prevent clashes with
     * other entities that may share the same functional id. It also enables ids to be case-insensitive.
     *
     * @param functionalId  The functional id of the entity. The object's toString() method is used to get a string
     *                      representation of the functional id.
     * @param type          The entity's type. This may be a superclass of the actual entity.
     * @param prefix        The prefix that is prepended to the {@link #functionalId} to create the full id under which
     *                      this entity will be stored. Eg, if the prefix of an {@link Id} is "user-", and the id is
     *                      "pete123", the entity will be stored under "user-pete123".
     * @param caseSensitive whether this id is case-sensitive.
     */
    protected Id(@NonNull Object functionalId, @NonNull Class<T> type, @NonNull String prefix, boolean caseSensitive) {
        this.functionalId = functionalId.toString();
        this.type = type;
        this.repositoryId = caseSensitive ? prefix + this.functionalId : prefix + this.functionalId.toLowerCase();
    }

    /**
     * Returns the id under which the entity will be stored in a repository. This may differ from the functional
     */
    @Override
    public final String toString() {
        return repositoryId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Id<?> id = (Id<?>) o;
        return type.equals(id.type) && repositoryId.equals(id.repositoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, repositoryId);
    }
}

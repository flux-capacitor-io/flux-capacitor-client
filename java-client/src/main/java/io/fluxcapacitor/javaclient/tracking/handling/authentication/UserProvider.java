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

package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.HasMessage;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;

/**
 * Service interface for managing {@link User} identities in Flux Capacitor.
 * <p>
 * A {@code UserProvider} is responsible for extracting, resolving, and injecting user identity information into
 * messages. It enables user-aware processing by:
 * <ul>
 *   <li>Resolving the current authenticated {@link User}</li>
 *   <li>Looking up users by ID or from message metadata</li>
 *   <li>Storing user metadata into messages for downstream correlation</li>
 * </ul>
 * <p>
 * Implementations of this interface can be registered via Java’s {@link ServiceLoader}. When multiple implementations
 * are found, they are combined using {@link #andThen(UserProvider)}.
 *
 * @see User
 */
public interface UserProvider {

    /**
     * Default {@code UserProvider} discovered via {@link ServiceLoader}. If multiple providers are found, they are
     * chained using {@link #andThen(UserProvider)}. May be {@code null} if no provider is registered.
     */
    UserProvider defaultUserProvider = Optional.of(ServiceLoader.load(UserProvider.class))
            .flatMap(loader -> loader.stream().map(Provider::get).reduce(UserProvider::andThen))
            .orElse(NoOpUserProvider.INSTANCE);

    /**
     * Returns the currently active user, typically injected by the current context.
     *
     * @return the active {@link User}, or {@link User#getCurrent()} if not explicitly provided
     */
    default User getActiveUser() {
        return User.getCurrent();
    }

    /**
     * Retrieves a {@link User} by their unique identifier.
     * <p>
     * This method is primarily used in {@code TestFixture}-based access control tests, such as when using
     * {@code whenCommandByUser(...)}, to simulate requests by a specific user.
     * <p>
     * Implementations may return {@code null} if the user cannot be found, or alternatively return a new, unprivileged
     * {@link User} instance. The latter approach allows tests to verify authorization behavior for unknown or default
     * users without requiring explicit user creation.
     *
     * @param userId the unique identifier of the user (typically a String or number)
     * @return the matching {@link User}, a default unprivileged {@link User}, or {@code null} if not found
     */
    User getUserById(Object userId);

    /**
     * Returns the {@link User} representing the system (non-human) identity. Typically used for scheduled messages,
     * internal services, etc.
     */
    User getSystemUser();

    /**
     * Extracts the {@link User} from a given {@link HasMessage} instance. Implementations may inspect message metadata
     * or payload to resolve the user identity.
     *
     * @param message the message containing potential user-related metadata
     * @return the resolved {@link User}, or {@code null} if no user info was found
     */
    User fromMessage(HasMessage message);

    /**
     * Checks if the given metadata contains user information that can be resolved by this provider.
     *
     * @param metadata the metadata to inspect
     * @return {@code true} if the metadata contains recognizable user information
     */
    boolean containsUser(Metadata metadata);

    /**
     * Removes any user-related metadata entries from the given {@link Metadata}.
     *
     * @param metadata the metadata to clean
     * @return a new {@link Metadata} instance without any user-specific keys
     */
    Metadata removeFromMetadata(Metadata metadata);

    /**
     * Adds user-related metadata to a message, overwriting existing values if present.
     *
     * @param metadata the original metadata
     * @param user     the user whose info should be added
     * @return new metadata including the user identity
     */
    default Metadata addToMetadata(Metadata metadata, User user) {
        return addToMetadata(metadata, user, false);
    }

    /**
     * Adds user-related metadata to a message.
     *
     * @param metadata the original metadata
     * @param user     the user to include
     * @param ifAbsent if {@code true}, metadata is only added if not already present
     * @return updated {@link Metadata} with user info conditionally added
     */
    Metadata addToMetadata(Metadata metadata, User user, boolean ifAbsent);

    /**
     * Combines this provider with another.
     * <p>
     * The returned provider will try this provider first, falling back to {@code other} if a user cannot be resolved.
     * This is useful for composing multiple resolution strategies.
     *
     * @param other another user provider to chain after this one
     * @return a new {@code UserProvider} that delegates to both providers
     */
    default UserProvider andThen(UserProvider other) {
        return new DelegatingUserProvider(this) {
            @Override
            public User getUserById(Object userId) {
                return Optional.ofNullable(super.getUserById(userId)).orElseGet(() -> other.getUserById(userId));
            }

            @Override
            public User getSystemUser() {
                return Optional.ofNullable(super.getSystemUser()).orElseGet(other::getSystemUser);
            }

            @Override
            public User fromMessage(HasMessage message) {
                return Optional.ofNullable(super.fromMessage(message)).orElseGet(() -> other.fromMessage(message));
            }

            @Override
            public boolean containsUser(Metadata metadata) {
                return super.containsUser(metadata) || other.containsUser(metadata);
            }

            @Override
            public Metadata removeFromMetadata(Metadata metadata) {
                metadata = super.removeFromMetadata(metadata);
                return other.removeFromMetadata(metadata);
            }

            @Override
            public Metadata addToMetadata(Metadata metadata, User user, boolean ifAbsent) {
                if (ifAbsent) {
                    return other.addToMetadata(super.addToMetadata(metadata, user, true), user, true);
                }
                boolean chained = delegate instanceof DelegatingUserProvider;
                return other.addToMetadata(super.addToMetadata(metadata, user, chained), user, true);
            }
        };
    }
}

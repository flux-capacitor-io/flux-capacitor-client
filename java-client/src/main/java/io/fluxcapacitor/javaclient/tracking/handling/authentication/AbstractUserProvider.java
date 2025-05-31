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
import lombok.AllArgsConstructor;

/**
 * Abstract base class for implementing {@link UserProvider}s that resolve user identities via a metadata key.
 * <p>
 * This implementation provides a reusable foundation for extracting, injecting, and managing {@link User} instances
 * within {@link Metadata}, commonly used in message handling scenarios. Most concrete {@code UserProvider}
 * implementations can extend this class to inherit standard behavior for:
 * <ul>
 *   <li>Retrieving a user from message metadata via a configured key</li>
 *   <li>Checking whether a user is present in metadata</li>
 *   <li>Inserting or removing user entries in metadata</li>
 * </ul>
 *
 * <h2>Metadata key</h2>
 * The default metadata key used for storing user objects is {@link #DEFAULT_USER_KEY}, which resolves to {@code "$user"}.
 * Custom keys can also be provided via the constructor for flexibility across different application contexts.
 *
 * @see User
 * @see UserProvider
 * @see Metadata
 * @see io.fluxcapacitor.javaclient.common.HasMessage
 */
@AllArgsConstructor
public abstract class AbstractUserProvider implements UserProvider {

    /**
     * Default key used in {@link Metadata} to store {@link User} objects.
     */
    public static String DEFAULT_USER_KEY = "$user";


    private final String metadataKey;
    private final Class<? extends User> userClass;

    /**
     * Constructs an {@code AbstractUserProvider} using the default metadata key {@link #DEFAULT_USER_KEY}.
     *
     * @param userClass the concrete user class this provider resolves
     */
    public AbstractUserProvider(Class<? extends User> userClass) {
        this(DEFAULT_USER_KEY, userClass);
    }

    /**
     * Extracts a {@link User} from the metadata of a message.
     * <p>
     * Uses the configured {@code metadataKey} to locate the user in the metadata of the given message.
     *
     * @param message the message containing metadata
     * @return the resolved {@link User}, or {@code null} if not found
     */
    @Override
    public User fromMessage(HasMessage message) {
        return message.getMetadata().get(metadataKey, userClass);
    }

    /**
     * Returns {@code true} if the metadata contains a user entry under the configured key.
     *
     * @param metadata the metadata to inspect
     * @return {@code true} if a user is present, otherwise {@code false}
     */
    @Override
    public boolean containsUser(Metadata metadata) {
        return metadata.containsKey(metadataKey);
    }

    /**
     * Removes the user entry from the metadata.
     *
     * @param metadata the original metadata
     * @return a new {@link Metadata} instance without the user entry
     */
    @Override
    public Metadata removeFromMetadata(Metadata metadata) {
        return metadata.without(metadataKey);
    }

    /**
     * Adds a {@link User} to the metadata using the configured key.
     *
     * @param metadata the original metadata
     * @param user     the user to add
     * @param ifAbsent whether to only add the user if it is not already present
     * @return updated metadata including the user
     */
    @Override
    public Metadata addToMetadata(Metadata metadata, User user, boolean ifAbsent) {
        return ifAbsent ? metadata.addIfAbsent(metadataKey, user) : metadata.with(metadataKey, user);
    }
}

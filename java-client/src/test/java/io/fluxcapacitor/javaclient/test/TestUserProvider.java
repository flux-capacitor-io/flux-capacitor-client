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

package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

import java.util.Optional;

/**
 * Default {@link UserProvider} used in test fixtures.
 * <p>
 * This provider wraps another {@link UserProvider} (typically the production default), but ensures that a non-null
 * user is always returned. If no active user is available via the delegate, it will return the
 * {@linkplain UserProvider#getSystemUser() system user} instead.
 * <p>
 * This behavior simplifies tests involving user authentication (such as web requests or @TrackSelf handlers),
 * since it removes the need to explicitly provide user context in most scenarios.
 *
 * <p><strong>Usage in Test Fixtures:</strong><br>
 * This provider is active by default in {@link io.fluxcapacitor.javaclient.test.TestFixture} instances.
 * If you wish to test behavior with unauthenticated users (e.g., as they would be seen in a real web request),
 * call {@link io.fluxcapacitor.javaclient.test.TestFixture#withProductionUserProvider()} to re-enable the production
 * default provider instead.
 *
 * <pre>{@code
 * // Uses TestUserProvider by default:
 * TestFixture fixture = TestFixture.create(MyHandler.class);
 *
 * // Use the actual production default provider instead (e.g., for unauthenticated behavior):
 * fixture = fixture.withDefaultUserProvider();
 * }</pre>
 *
 * @see UserProvider
 * @see io.fluxcapacitor.javaclient.test.TestFixture#withProductionUserProvider()
 */
@AllArgsConstructor
public class TestUserProvider implements UserProvider {

    /**
     * Delegate provider, usually the production default.
     * <p>
     * All calls except {@link #getActiveUser()} are forwarded directly to this delegate.
     */
    @Delegate
    private final UserProvider delegate;

    /**
     * Returns the currently active user.
     * <p>
     * If the delegate returns {@code null}, this method returns the {@linkplain #getSystemUser() system user}
     * instead. This guarantees an identity is always available in test scenarios.
     *
     * @return the active user, or the system user if none is active
     */
    @Override
    public User getActiveUser() {
        return Optional.ofNullable(delegate.getActiveUser()).orElseGet(delegate::getSystemUser);
    }
}

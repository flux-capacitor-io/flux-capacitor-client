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

import lombok.extern.slf4j.Slf4j;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * A JUnit 5 {@link TestExecutionListener} that shuts down all active {@link TestFixture} instances after each test.
 * <p>
 * This ensures that Flux Capacitor resources such as schedulers, threads, or stateful components are properly
 * released between test executions.
 * <p>
 * This listener is useful in test environments that rely on side-effectful or stateful behaviors (e.g. schedules,
 * in-memory gateways, spies), especially when running multiple tests within the same JVM.
 *
 * <p><strong>Usage:</strong> Register this listener in your {@code junit-platform.properties} file:
 * <pre>{@code
 * junit.platform.listeners.default = io.fluxcapacitor.javaclient.test.TestFixtureExecutionListener
 * }</pre>
 *
 * @see TestFixture#shutDownActiveFixtures()
 * @see TestFixture
 */
@Slf4j
public class TestFixtureExecutionListener implements TestExecutionListener {

    /**
     * Invoked automatically by the JUnit 5 test engine after each test case completes.
     * <p>
     * This will invoke {@link TestFixture#shutDownActiveFixtures()}, which closes all Flux components
     * registered to the current thread.
     */
    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        TestFixture.shutDownActiveFixtures();
    }
}

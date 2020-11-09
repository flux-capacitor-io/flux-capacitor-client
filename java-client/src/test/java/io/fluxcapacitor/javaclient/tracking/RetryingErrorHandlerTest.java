/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.javaclient.MockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class RetryingErrorHandlerTest {

    private final MockException exception = new MockException();
    private final Runnable mockTask = mock(Runnable.class);

    @BeforeEach
    void setUp() {
        doThrow(exception).when(mockTask).run();
    }

    @Test
    void testRetryCountCorrect() throws Exception {
        RetryingErrorHandler subject = new RetryingErrorHandler(3, Duration.ofMillis(10), e -> true, false, true);
        subject.handleError(exception, "mock exception", mockTask);
        verify(mockTask, times(3)).run();
    }

    @Test
    void testThrowsExceptionIfDesired() {
        RetryingErrorHandler subject = new RetryingErrorHandler(1, Duration.ofMillis(10), e -> true, true, true);
        assertThrows(MockException.class, () -> subject.handleError(exception, "mock exception", mockTask));
    }

    @Test
    void testDontRetryIfErrorFilterFails() throws Exception {
        RetryingErrorHandler subject = new RetryingErrorHandler(1, Duration.ofMillis(10), e -> false, false, true);
        subject.handleError(exception, "mock exception", mockTask);
        verifyNoInteractions(mockTask);
    }

    @Test
    void testThrowIfErrorFilterFails() {
        RetryingErrorHandler subject = new RetryingErrorHandler(1, Duration.ofMillis(10), e -> false, true, true);
        assertThrows(MockException.class, () -> subject.handleError(exception, "mock exception", mockTask));
    }
}

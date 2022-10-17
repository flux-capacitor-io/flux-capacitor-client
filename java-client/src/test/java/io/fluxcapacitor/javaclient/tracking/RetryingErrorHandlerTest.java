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
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class RetryingErrorHandlerTest {

    private final MockException exception = new MockException();
    private final Callable<?> mockTask = mock(Callable.class);

    @BeforeEach
    void setUp() throws Exception {
        doThrow(exception).when(mockTask).call();
    }

    @Test
    void testRetryCountCorrect() throws Exception {
        RetryingErrorHandler subject = new RetryingErrorHandler(3, Duration.ofMillis(10), e -> true, false, true);
        subject.handleError(exception, "mock exception", mockTask);
        verify(mockTask, times(4)).call();
    }

    @Test
    void testThrowsExceptionIfDesired() {
        RetryingErrorHandler subject = new RetryingErrorHandler(1, Duration.ofMillis(10), e -> true, true, true);
        assertThrows(MockException.class, () -> subject.handleError(exception, "mock exception", mockTask));
    }

    @Test
    void testDontRetryIfErrorFilterFails() {
        RetryingErrorHandler subject = new RetryingErrorHandler(1, Duration.ofMillis(10), e -> false, false, true);
        subject.handleError(exception, "mock exception", mockTask);
        verifyNoInteractions(mockTask);
    }

    @Test
    void testThrowIfErrorFilterFails() {
        RetryingErrorHandler subject = new RetryingErrorHandler(1, Duration.ofMillis(10), e -> false, true, true);
        assertThrows(MockException.class, () -> subject.handleError(exception, "mock exception", mockTask));
    }

    @Test
    void retryIsSuccessful() {
        RetryingErrorHandler subject = new RetryingErrorHandler(
                10, Duration.ofMillis(10), e -> true, true, true);
        assertEquals("success", subject.handleError(exception, "mock exception",
                                                    new Repairable("success", 5)));
    }

    @Test
    void retryIsNotSuccessful() {
        RetryingErrorHandler subject = new RetryingErrorHandler(
                3, Duration.ofMillis(10), e -> true, true, true);
        assertThrows(MockException.class, () -> subject.handleError(exception, "mock exception",
                                                    new Repairable("success", 5)));
    }

    @Test
    void returnsButDoesntThrowErrorIfUnsuccessfulAfterRetry() {
        RetryingErrorHandler subject = new RetryingErrorHandler(
                3, Duration.ofMillis(10), e -> true, false, true);
        assertTrue(subject.handleError(exception, "mock exception",
                                       new Repairable("success", 5)) instanceof MockException);
    }

    @Nested
    class RetryingConsumerTests {

        @Test
        void retryIsSuccessful() {
            TestFixture.createAsync(new Handler(new AtomicInteger(2)))
                    .whenCommand("success").expectResult("success");
        }

        @Test
        void retryIsUnsuccessful() {
            TestFixture.createAsync(new Handler(new AtomicInteger(4)))
                    .whenCommand("success").expectExceptionalResult(MockException.class);
        }

        @Test
        void retryIsSuccessful_future() {
            TestFixture.createAsync(new Handler(new AtomicInteger(2)))
                    .whenCommand(123).expectResult(123);
        }

        @Test
        void retryIsUnsuccessful_future() {
            TestFixture.createAsync(new Handler(new AtomicInteger(4)))
                    .whenCommand(123).expectExceptionalResult(MockException.class);
        }

        @Consumer(errorHandler = CustomErrorHandler.class)
        @AllArgsConstructor
        class Handler {
            AtomicInteger remainingFailures;

            @HandleCommand
            String handle(String command) {
                if (remainingFailures.getAndDecrement() > 0) {
                    throw new MockException();
                }
                return command;
            }

            @HandleCommand
            CompletableFuture<?> handle(Integer command) {
                if (remainingFailures.getAndDecrement() > 0) {
                    return CompletableFuture.runAsync(() -> {
                        throw new MockException();
                    });
                }
                return CompletableFuture.supplyAsync(() -> command);
            }
        }

    }

    static class CustomErrorHandler extends RetryingErrorHandler {
        public CustomErrorHandler() {
            super(2, Duration.ofMillis(10), e -> true, false, true);
        }
    }

    @Value
    private static class Repairable implements Callable<Object> {
        Object delayedResult;
        AtomicInteger remainingFailures;

        public Repairable(Object delayedResult, int maxFailures) {
            this.delayedResult = delayedResult;
            this.remainingFailures = new AtomicInteger(maxFailures);
        }

        @Override
        public Object call() {
            if (remainingFailures.getAndDecrement() > 0) {
                throw new MockException();
            }
            return delayedResult;
        }
    }
}

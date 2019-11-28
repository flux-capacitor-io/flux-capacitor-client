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
        RetryingErrorHandler subject = new RetryingErrorHandler(3, Duration.ofMillis(10), e -> true, false);
        subject.handleError(exception, "mock exception", mockTask);
        verify(mockTask, times(3)).run();
    }

    @Test
    void testThrowsExceptionIfDesired() {
        RetryingErrorHandler subject = new RetryingErrorHandler(1, Duration.ofMillis(10), e -> true, true);
        assertThrows(MockException.class, () -> subject.handleError(exception, "mock exception", mockTask));
    }

    @Test
    void testDontRetryIfErrorFilterFails() throws Exception {
        RetryingErrorHandler subject = new RetryingErrorHandler(1, Duration.ofMillis(10), e -> false, false);
        subject.handleError(exception, "mock exception", mockTask);
        verifyNoInteractions(mockTask);
    }

    @Test
    void testThrowIfErrorFilterFails() {
        RetryingErrorHandler subject = new RetryingErrorHandler(1, Duration.ofMillis(10), e -> false, true);
        assertThrows(MockException.class, () -> subject.handleError(exception, "mock exception", mockTask));
    }
}
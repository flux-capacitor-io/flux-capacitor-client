package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.javaclient.MockException;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;

import static org.mockito.Mockito.*;

public class RetryingErrorHandlerTest {

    private final MockException exception = new MockException();
    private final Runnable mockTask = mock(Runnable.class);

    @Before
    public void setUp() throws Exception {
        doThrow(exception).when(mockTask).run();
    }

    @Test
    public void testRetryCountCorrect() throws Exception {
        RetryingErrorHandler subject = new RetryingErrorHandler(3, Duration.ofMillis(10), e -> true, false);
        subject.handleError(exception, "mock exception", mockTask);
        verify(mockTask, times(3)).run();
    }

    @Test(expected = MockException.class)
    public void testThrowsExceptionIfDesired() throws Exception {
        RetryingErrorHandler subject = new RetryingErrorHandler(1, Duration.ofMillis(10), e -> true, true);
        subject.handleError(exception, "mock exception", mockTask);
    }

    @Test
    public void testDontRetryIfErrorFilterFails() throws Exception {
        RetryingErrorHandler subject = new RetryingErrorHandler(1, Duration.ofMillis(10), e -> false, false);
        subject.handleError(exception, "mock exception", mockTask);
        verifyZeroInteractions(mockTask);
    }

    @Test(expected = MockException.class)
    public void testThrowIfErrorFilterFails() throws Exception {
        RetryingErrorHandler subject = new RetryingErrorHandler(1, Duration.ofMillis(10), e -> false, true);
        subject.handleError(exception, "mock exception", mockTask);
    }
}
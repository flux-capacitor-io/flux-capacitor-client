package io.fluxcapacitor.javaclient.common;

import io.fluxcapacitor.javaclient.tracking.handling.LocalHandler;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Executable;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
public class ClientUtils {

    public static void waitForResults(Duration maxDuration, Collection<? extends CompletableFuture<?>> futures) {
        Instant deadline = Instant.now().plus(maxDuration);
        for (CompletableFuture<?> f : futures) {
            try {
                f.get(Math.max(0, Duration.between(Instant.now(), deadline).toMillis()), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Thread was interrupted before receiving all expected results", e);
                return;
            } catch (TimeoutException e) {
                log.warn("Timed out before having received all expected results", e);
                return;
            } catch (ExecutionException ignore) {
            }
        }
    }

    public static void tryRun(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("Task {} failed", task, e);
        }
    }

    public static boolean isLocalHandlerMethod(Class<?> target, Executable method) {
        LocalHandler localHandler = method.getAnnotation(LocalHandler.class);
        if (localHandler == null) {
            localHandler = target.getAnnotation(LocalHandler.class);
        }
        return localHandler != null && localHandler.value();
    }

}

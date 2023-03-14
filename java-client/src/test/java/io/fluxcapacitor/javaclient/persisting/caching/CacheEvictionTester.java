package io.fluxcapacitor.javaclient.persisting.caching;

import io.fluxcapacitor.javaclient.common.DirectExecutor;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;

public class CacheEvictionTester {

    /**
     * Start with -Xmx10M
     */
    @SneakyThrows
    public static void main(final String[] args) {
        try (DefaultCache subject = new DefaultCache(2, DirectExecutor.INSTANCE)) {
            subject.put("foo", new Object());

            try {
                var o = new int[10][10][10][10][10][10][10][10][10][10];
            } catch (OutOfMemoryError err) {
                System.out.println("Out of memory ignored");
            } finally {
                Thread.sleep(10);
                Assertions.assertFalse(subject.containsKey("foo"));
                System.out.println("JVM cleared the value");
            }

        }
    }
}

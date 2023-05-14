package io.fluxcapacitor.proxy;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.GracefulShutdownHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import static io.undertow.Handlers.path;
import static java.lang.Runtime.getRuntime;

@Slf4j
public class ProxyServer {
    public static void main(final String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> log.error("Uncaught error", e));
        int port = Integer.parseInt(Optional.ofNullable(System.getenv("PROXY_PORT")).orElse("80"));
        Client client = Optional.ofNullable(System.getenv("FLUX_URL")).<Client>map(url -> WebSocketClient.newInstance(
                WebSocketClient.ClientConfig.builder().name("$proxy").serviceBaseUrl(url).build()))
                .orElseThrow(() -> new IllegalStateException("FLUX_URL environment variable is not set"));
        start(port, new ProxyRequestHandler(client));
    }

    static Registration start(int port, ProxyRequestHandler proxyHandler) {
        Undertow server = Undertow.builder().addHttpListener(port, "0.0.0.0")
                .setHandler(path().addPrefixPath("/", proxyHandler))
                .build();
        server.start();
        log.info("Flux Capacitor proxy server running on port {}", port);
        return () -> {
            proxyHandler.close();
            server.stop();
        };
    }

    private static GracefulShutdownHandler addShutdownHandler(HttpHandler httpHandler, Runnable proxyShutdownHandler) {
        GracefulShutdownHandler shutdownHandler = new GracefulShutdownHandler(httpHandler);
        Runnable task = () -> {
            proxyShutdownHandler.run();
            shutdownHandler.shutdown();
            try {
                shutdownHandler.awaitShutdown(1000);
            } catch (InterruptedException e) {
                log.warn("Thread to kill server was interrupted");
                Thread.currentThread().interrupt();
            }
        };
        FluxCapacitor.getOptionally().ifPresentOrElse(fc -> fc.beforeShutdown(task),
                                                      () -> getRuntime().addShutdownHook(new Thread(task)));
        return shutdownHandler;
    }
}

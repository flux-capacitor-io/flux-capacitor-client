package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;

public class WebTestUtils {

    private static volatile boolean integrationServerStarted;
    public static int integrationTestPort = 9000;

    public static synchronized TestFixture createWebServerTestFixture(PathHandler pathHandler, Object... handlers) {
        if (!integrationServerStarted) {
            Undertow.builder().addHttpListener(integrationTestPort, "0.0.0.0").setHandler(pathHandler).build().start();
            integrationServerStarted = true;
        }
        return TestFixture.createAsync(DefaultFluxCapacitor.builder().registerWebServer(integrationTestPort), handlers);
    }
}

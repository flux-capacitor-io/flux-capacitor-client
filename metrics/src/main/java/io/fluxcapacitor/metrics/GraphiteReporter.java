/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

package io.fluxcapacitor.metrics;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import io.fluxcapacitor.common.api.ClientAction;
import io.fluxcapacitor.common.api.tracking.AppendAction;
import io.fluxcapacitor.common.api.tracking.ReadAction;
import io.fluxcapacitor.common.api.tracking.StorePositionAction;
import io.fluxcapacitor.common.handling.Handle;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class GraphiteReporter extends MetricsReporter {

    private final MetricRegistry metrics;
    private final com.codahale.metrics.graphite.GraphiteReporter reporter;

    public static void main(final String[] args) {
        String fluxCapacitorUrl = System.getProperty("fluxCapacitorUrl", "ws://localhost:8080");
        String host = System.getProperty("graphiteHostName", "localhost");
        int port = Optional.ofNullable(System.getProperty("port")).map(Integer::valueOf).orElse(2003);
        new GraphiteReporter(host, port, fluxCapacitorUrl).start();
    }

    public GraphiteReporter(String host, int port, String fluxCapacitorUrl) {
        super(fluxCapacitorUrl);
        this.metrics = new MetricRegistry();
        this.reporter = com.codahale.metrics.graphite.GraphiteReporter.forRegistry(metrics)
                .prefixedWith("fluxCapacitorClient")
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(new Graphite(new InetSocketAddress(host, port)));
    }

    @Override
    public void start() {
        reporter.start(10, TimeUnit.SECONDS);
        super.start();
    }

    @Handle
    public void handle(AppendAction action) {
        getMeter(action, action.getLog()).mark(action.getSize());
    }

    @Handle
    public void handle(ReadAction action) {
        getMeter(action, action.getLog()).mark(action.getSize());
    }

    @Handle
    public void handle(StorePositionAction action) {
        getMeter(action, action.getLog()).mark();
    }

    private Meter getMeter(ClientAction action, String log) {
        String meterName = String.format("%s/%s/%s", action.getClass().getSimpleName(), action.getClient(), log);
        return metrics.meter(meterName);
    }

}

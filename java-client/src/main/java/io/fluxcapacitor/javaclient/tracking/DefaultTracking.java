package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.Registration;
import lombok.AllArgsConstructor;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;

@AllArgsConstructor
public class DefaultTracking implements Tracking {

    private final Annotation handlerAnnotation;
    private final TrackingClient trackingClient;
    private final List<ConsumerConfiguration> consumerConfigurations;
    private final AtomicBoolean started = new AtomicBoolean();

    @Override
    public Registration start(List<Object> handlers) {
        if (started.compareAndSet(false, true)) {
            Map<ConsumerConfiguration, List<Object>> groups = handlers.stream().collect(groupingBy(
                    handler -> consumerConfigurations.stream().filter(config -> config.getHandlerFilter().test(handler))
                            .findFirst().orElseThrow(() -> new TrackingException(
                                    format("Failed to find a suitable group for handler %s", handler)))));

        } else {
            throw new TrackingException("Tracking has already started");
        }
        return null;
    }

}

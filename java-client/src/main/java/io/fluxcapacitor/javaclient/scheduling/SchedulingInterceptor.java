/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
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

package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.configuration.ApplicationProperties;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.ensureAccessible;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedMethods;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getTypeAnnotation;
import static io.fluxcapacitor.javaclient.FluxCapacitor.currentIdentityProvider;
import static io.fluxcapacitor.javaclient.tracking.IndexUtils.millisFromIndex;
import static java.lang.String.format;
import static java.time.Duration.between;
import static java.time.Instant.ofEpochMilli;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Optional.ofNullable;

@Slf4j
public class SchedulingInterceptor implements DispatchInterceptor, HandlerInterceptor {

    private static final Function<String, Optional<CronExpression>> cronExpression = memoize(pattern -> {
        pattern = ApplicationProperties.substituteProperties(pattern);
        return Periodic.DISABLED.equals(pattern) ? Optional.empty() : Optional.of(new CronExpression(pattern));
    });

    @Override
    public Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler) {
        Object target = handler.getTarget();
        List<Method> methods = getAnnotatedMethods(target, HandleSchedule.class);
        for (Method method : methods) {
            Periodic periodic = method.getAnnotation(Periodic.class);
            if (method.getParameterCount() > 0) {
                Class<?> type = method.getParameters()[0].getType();
                periodic = periodic == null ? getTypeAnnotation(type, Periodic.class) : periodic;
                try {
                    initializePeriodicSchedule(type, periodic);
                } catch (Exception e) {
                    log.error("Failed to initialize periodic schedule on method {}. Continuing...", method, e);
                }
            }
        }
        return HandlerInterceptor.super.wrap(handler);
    }

    protected void initializePeriodicSchedule(Class<?> payloadType, Periodic periodic) {
        if (periodic == null) {
            return;
        }
        if (periodic.cron().isBlank() && periodic.delay() <= 0) {
            throw new IllegalStateException(format(
                    "Periodic annotation on type %s is invalid. "
                    + "Period should be a positive number of  milliseconds.", payloadType));
        }
        if (periodic.autoStart()) {
            FluxCapacitor fluxCapacitor = FluxCapacitor.get();
            Instant firstDeadline = firstDeadline(periodic, fluxCapacitor.clock().instant());
            if (firstDeadline == null) {
                return; //cron schedule is disabled
            }
            String scheduleId = periodic.scheduleId().isEmpty() ? payloadType.getName() : periodic.scheduleId();
            Object payload;
            try {
                payload = ensureAccessible(payloadType.getConstructor()).newInstance();
            } catch (Exception e) {
                log.error("No default constructor found on @Periodic type: {}. "
                          + "Add a public default constructor or initialize this periodic schedule by hand",
                          payloadType, e);
                return;
            }
            Metadata metadata = ofNullable(fluxCapacitor.userProvider()).flatMap(
                            p -> ofNullable(p.getSystemUser()).map(u -> p.addToMetadata(Metadata.empty(), u)))
                    .orElse(Metadata.empty());
            fluxCapacitor.scheduler().schedule(new Schedule(
                    payload, metadata, scheduleId, firstDeadline), true);
        }
    }

    protected Instant firstDeadline(Periodic periodic, Instant now) {
        if (periodic.initialDelay() >= 0) {
            return now.plusMillis(periodic.timeUnit().toMillis(periodic.initialDelay()));
        }
        return nextDeadline(periodic, now);
    }

    protected Instant nextDeadline(Periodic periodic, Instant now) {
        if (periodic.cron().isBlank()) {
            return now.plusMillis(periodic.timeUnit().toMillis(periodic.delay()));
        }
        return cronExpression.apply(periodic.cron())
                .map(e -> e.nextTimeAfter(now.atZone(ZoneId.of(periodic.timeZone()))).toInstant()).orElse(null);
    }

    @Override
    public Message interceptDispatch(Message message, MessageType messageType) {
        if (messageType == MessageType.SCHEDULE) {
            message = message.withMetadata(
                    message.getMetadata()
                            .with(Schedule.scheduleIdMetadataKey, ((Schedule) message).getScheduleId()));
        }
        return message;
    }

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    HandlerInvoker invoker) {
        return m -> {
            if (m.getMessageType() == MessageType.SCHEDULE) {
                long deadline = millisFromIndex(m.getIndex());
                Periodic periodic = ofNullable(invoker.getMethod()).map(method -> method.getAnnotation(Periodic.class))
                        .or(() -> ofNullable(getTypeAnnotation(m.getPayloadClass(), Periodic.class)))
                        .orElse(null);
                if (periodic != null && !periodic.cron().isBlank() && cronExpression.apply(periodic.cron()).isEmpty()) {
                    log.warn("Periodic scheduling is disabled for {}. Ignoring schedule {}.",
                             m.getPayloadClass(), m.getMessageId());
                    return null; //schedule is disabled. Don't invoke handler anymore.
                }
                Object result;
                Instant now = ofEpochMilli(deadline);
                try {
                    result = function.apply(m);
                } catch (Throwable e) {
                    if (periodic != null && periodic.continueOnError()) {
                        if (periodic.delayAfterError() >= 0) {
                            schedule(m, now.plusMillis(periodic.timeUnit().toMillis(periodic.delayAfterError())));
                        } else {
                            schedule(m, nextDeadline(periodic, now));
                        }
                    }
                    throw e;
                }
                if (result instanceof TemporalAmount) {
                    schedule(m, now.plus((TemporalAmount) result));
                } else if (result instanceof TemporalAccessor) {
                    schedule(m, Instant.from((TemporalAccessor) result));
                } else if (result instanceof Schedule) {
                    schedule((Schedule) result);
                } else if (result != null) {
                    Metadata metadata = m.getMetadata();
                    Object nextPayload = result;
                    if (result instanceof Message) {
                        metadata = ((Message) result).getMetadata();
                        nextPayload = ((Message) result).getPayload();
                    }
                    if (nextPayload != null && m.getPayloadClass().isAssignableFrom(nextPayload.getClass())) {
                        if (periodic == null) {
                            Instant dispatched = m.getTimestamp();
                            Duration previousDelay = between(dispatched, now);
                            if (previousDelay.compareTo(Duration.ZERO) > 0) {
                                schedule(nextPayload, metadata, now.plus(previousDelay));
                            } else {
                                log.info("Delay between the time this schedule was created and scheduled is <= 0, "
                                         + "rescheduling with delay of 1 minute");
                                schedule(nextPayload, metadata, now.plus(Duration.of(1, MINUTES)));
                            }
                        } else {
                            schedule(nextPayload, metadata, nextDeadline(periodic, now));
                        }
                    } else if (periodic != null) {
                        schedule(m, nextDeadline(periodic, now));
                    }
                } else if (periodic != null) {
                    schedule(m, nextDeadline(periodic, now));
                }
                return result;
            }
            return function.apply(m);
        };
    }

    private void schedule(DeserializingMessage message, Instant instant) {
        schedule(message.getPayload(), message.getMetadata(), instant);
    }

    private void schedule(Object payload, Metadata metadata, Instant instant) {
        if (instant != null) {
            schedule(new Schedule(payload, metadata, metadata.getOrDefault(
                    Schedule.scheduleIdMetadataKey, currentIdentityProvider().nextTechnicalId()), instant));
        }
    }

    private void schedule(Schedule schedule) {
        try {
            FluxCapacitor.get().scheduler().schedule(schedule);
        } catch (Exception e) {
            log.error("Failed to reschedule a {}", schedule.getPayloadClass(), e);
        }
    }
}

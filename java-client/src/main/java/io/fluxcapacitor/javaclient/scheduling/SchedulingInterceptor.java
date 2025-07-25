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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.ensureAccessible;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedMethods;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getTypeAnnotation;
import static io.fluxcapacitor.javaclient.FluxCapacitor.currentIdentityProvider;
import static io.fluxcapacitor.javaclient.scheduling.CronExpression.parseCronExpression;
import static io.fluxcapacitor.javaclient.tracking.IndexUtils.millisFromIndex;
import static java.lang.String.format;
import static java.time.Duration.between;
import static java.time.Instant.ofEpochMilli;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Optional.ofNullable;

/**
 * Intercepts scheduled messages to handle periodic scheduling logic.
 * <p>
 * This interceptor enables powerful scheduling features such as:
 * <ul>
 *     <li><strong>Initial scheduling of @Periodic types:</strong> When a handler method or its parameter type is annotated
 *     with {@link io.fluxcapacitor.javaclient.scheduling.Periodic}, this interceptor will automatically initialize the
 *     schedule at startup if {@code autoStart=true}.</li>
 *     <li><strong>Rescheduling on success or failure:</strong> Upon successful or failed invocation of a scheduled handler,
 *     this interceptor can reschedule the next invocation based on the result or according to cron/delay rules.</li>
 *     <li><strong>Metadata injection:</strong> Adds the {@code scheduleId} metadata to outgoing scheduled messages to
 *     support correlation and rescheduling.</li>
 *     <li><strong>Dynamic behavior based on handler return value:</strong> Supports dynamic rescheduling based on return
 *     types like {@link TemporalAmount}, {@link TemporalAccessor}, {@link Schedule}, or even a replacement payload object.</li>
 * </ul>
 *
 * <p>
 * Additionally, if the scheduled handler throws a {@link io.fluxcapacitor.javaclient.scheduling.CancelPeriodic} exception,
 * the schedule is permanently cancelled without logging an error.
 *
 * <p>
 * This interceptor is registered automatically with {@link FluxCapacitor}.
 *
 * @see io.fluxcapacitor.javaclient.scheduling.Periodic
 * @see io.fluxcapacitor.javaclient.scheduling.CancelPeriodic
 * @see io.fluxcapacitor.javaclient.scheduling.Schedule
 */
@Slf4j
public class SchedulingInterceptor implements DispatchInterceptor, HandlerInterceptor {

    private static final Function<String, Optional<CronExpression>> cronExpression = memoize(pattern -> {
        pattern = ApplicationProperties.substituteProperties(pattern);
        return Periodic.DISABLED.equals(pattern) ? Optional.empty() : Optional.of(parseCronExpression(pattern));
    });

    @Override
    public Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler) {
        List<Method> methods = getAnnotatedMethods(handler.getTargetClass(), HandleSchedule.class);
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
            fluxCapacitor.messageScheduler().schedule(new Schedule(
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
    public Message interceptDispatch(Message message, MessageType messageType, String topic) {
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
        return schedule -> {
            if (schedule.getMessageType() == MessageType.SCHEDULE) {
                long deadline = millisFromIndex(schedule.getIndex());
                Periodic periodic = ofNullable(invoker.getMethod()).map(method -> method.getAnnotation(Periodic.class))
                        .or(() -> ofNullable(getTypeAnnotation(schedule.getPayloadClass(), Periodic.class)))
                        .orElse(null);
                if (periodic != null && !periodic.cron().isBlank() && cronExpression.apply(periodic.cron()).isEmpty()) {
                    log.warn("Periodic scheduling is disabled for {}. Ignoring schedule {}.",
                             schedule.getPayloadClass(), schedule.getMessageId());
                    return null; //schedule is disabled. Don't invoke handler anymore.
                }
                Object result;
                Instant now = ofEpochMilli(deadline);
                try {
                    result = function.apply(schedule);
                } catch (Throwable e) {
                    return handleExceptionalResult(e, schedule, now, periodic);
                }
                return handleResult(result, schedule, now, periodic);
            }
            return function.apply(schedule);
        };
    }

    protected Object handleResult(Object result, DeserializingMessage schedule, Instant now, Periodic periodic) {
        if (result instanceof CompletionStage<?> f) {
            f.whenComplete((r, e) -> {
                if (e == null) {
                    handleResult(r, schedule, now, periodic);
                } else {
                    try {
                        handleExceptionalResult(e, schedule, now, periodic);
                    } catch (Throwable ignored) {
                    }
                }
            });
            return result;
        } else if (result instanceof TemporalAmount) {
            schedule(schedule, now.plus((TemporalAmount) result));
        } else if (result instanceof TemporalAccessor) {
            schedule(schedule, Instant.from((TemporalAccessor) result));
        } else if (result instanceof Schedule) {
            schedule((Schedule) result);
        } else if (result != null) {
            Metadata metadata = schedule.getMetadata();
            Object nextPayload = result;
            if (result instanceof Message) {
                metadata = ((Message) result).getMetadata();
                nextPayload = ((Message) result).getPayload();
            }
            if (nextPayload != null && schedule.getPayloadClass().isAssignableFrom(nextPayload.getClass())) {
                if (periodic == null) {
                    Instant dispatched = schedule.getTimestamp();
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
                schedule(schedule, nextDeadline(periodic, now));
            }
        } else if (periodic != null) {
            schedule(schedule, nextDeadline(periodic, now));
        }
        return result;
    }

    @SneakyThrows
    protected Object handleExceptionalResult(Throwable result, DeserializingMessage schedule, Instant now,
                                             Periodic periodic) {
        if (result instanceof CancelPeriodic) {
            String scheduleId = ofNullable(schedule.getMetadata().get(Schedule.scheduleIdMetadataKey))
                    .or(() -> ofNullable(periodic).map(Periodic::scheduleId))
                    .orElseGet(() -> schedule.getPayloadClass().getName());
            log.info("Periodic schedule {} will be cancelled.", scheduleId);
            FluxCapacitor.get().messageScheduler().cancelSchedule(scheduleId);
            return null;
        }
        if (periodic != null && periodic.continueOnError()) {
            if (periodic.delayAfterError() >= 0) {
                schedule(schedule, now.plusMillis(periodic.timeUnit().toMillis(periodic.delayAfterError())));
            } else {
                schedule(schedule, nextDeadline(periodic, now));
            }
        }
        throw result;
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
            FluxCapacitor.get().messageScheduler().schedule(schedule);
        } catch (Exception e) {
            log.error("Failed to reschedule a {}", schedule.getPayloadClass(), e);
        }
    }
}

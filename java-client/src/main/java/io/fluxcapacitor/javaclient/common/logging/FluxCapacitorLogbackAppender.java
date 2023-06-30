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

package io.fluxcapacitor.javaclient.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Context;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

@Slf4j
public class FluxCapacitorLogbackAppender extends AppenderBase<ILoggingEvent> {

    public static void attach() {
        Context loggerContext = (Context) LoggerFactory.getILoggerFactory();
        FluxCapacitorLogbackAppender appender = new FluxCapacitorLogbackAppender();
        LevelFilter filter = new LevelFilter();
        filter.setLevel(Level.WARN);
        appender.addFilter(filter);
        appender.setContext(loggerContext);
        appender.start();
        Logger rootLogger = getRootLogger();
        rootLogger.addAppender(appender);
    }

    public static void detach() {
        Logger rootLogger = getRootLogger();
        Iterator<Appender<ILoggingEvent>> iterator = rootLogger.iteratorForAppenders();
        List<Appender<ILoggingEvent>> appenders = new ArrayList<>();
        while (iterator.hasNext()) {
            Appender<ILoggingEvent> appender = iterator.next();
            if (appender instanceof FluxCapacitorLogbackAppender) {
                appenders.add(appender);
            }
        }
        appenders.forEach(rootLogger::detachAppender);
    }

    @Override
    protected void append(ILoggingEvent event) {
        try {
            Optional<Throwable> throwable =
                    ofNullable((ThrowableProxy) event.getThrowableProxy()).map(ThrowableProxy::getThrowable);
            Metadata metadata = ofNullable(DeserializingMessage.getCurrent())
                    .map(DeserializingMessage::getMetadata).orElse(Metadata.empty());
            metadata = metadata.with(
                    "stackTrace", format("[%s] %s %s - %s%s", event.getThreadName(), event.getLevel(),
                                         event.getLoggerName(), event.getFormattedMessage(),
                                         throwable.map(e -> "\n" + getStackTrace(e)).orElse("")),
                    "level", event.getLevel().toString(),
                    "loggerName", event.getLoggerName());
            if (throwable.isPresent()) {
                Throwable e = throwable.get();
                metadata = metadata.with(
                        "error", e.getClass().getSimpleName(),
                        "message", event.getFormattedMessage(),
                        "errorMessage", isBlank(e.getMessage()) ? event.getFormattedMessage() : e.getMessage());
                StackTraceElement[] stackTraceElements =
                        ofNullable(e.getStackTrace()).filter(s -> s.length > 0).orElse(null);
                if (stackTraceElements != null) {
                    metadata = metadata.with(
                            "traceElement", stackTraceElements[0].toString());
                }
            } else {
                metadata = metadata.with(
                        "message", event.getFormattedMessage(), "errorMessage", event.getFormattedMessage());
            }
            FluxCapacitor.get().errorGateway().report(
                    event.getLevel() == Level.WARN ? new ConsoleWarning() : new ConsoleError(), metadata);
        } catch (Throwable e) {
            log.info("Failed to publish console error", e);
        }
    }

    private static Logger getRootLogger() {
        return (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    }

}

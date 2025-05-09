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

package io.fluxcapacitor.javaclient.web;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Annotation used on a handler class that functions as a web socket endpoint for a single websocket session.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public @interface SocketEndpoint {

    /**
     * Configuration of the keep-alive mechanism that periodically pings the client. Enabled by default.
     */
    AliveCheck aliveCheck() default @AliveCheck;


    /**
     * Annotation used to configure a keep-alive mechanism for web socket sessions.
     * This mechanism periodically pings the connected client to ensure the session remains active.
     */
    @interface AliveCheck {

        /**
         * Flag to enable or disable the keep-alive mechanism. Defaults to {@code true}, i.e. enabled.
         */
        boolean value() default true;

        /**
         * Returns the unit for {@link #pingDelay()} and {@link #pingTimeout()}. Defaults to {@link TimeUnit#SECONDS}.
         */
        TimeUnit timeUnit() default SECONDS;

        /**
         * Returns the time between two pings in {@link #timeUnit()} units.
         */
        long pingDelay() default 60;

        /**
         * Returns the time allowed after a ping to get a pong response in {@link #timeUnit()} units. If no pong is
         * received in time, the session will be automatically closed.
         */
        long pingTimeout() default 60;
    }

}

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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.tracking.handling.HandleMessage;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a handler for {@link MessageType#WEBRESPONSE} messages.
 * <p>
 * This is typically used to inspect response messages emitted by Flux applications that originally handled
 * {@link MessageType#WEBREQUEST} messages.
 * </p>
 *
 * @see HandleWeb
 * @see MessageType#WEBRESPONSE
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@HandleMessage(MessageType.WEBRESPONSE)
public @interface HandleWebResponse {

    /**
     * If {@code true}, disables this handler during discovery.
     */
    boolean disabled() default false;
}

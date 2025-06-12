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
 * Marks a method or meta-annotation as a handler for incoming web requests ({@link MessageType#WEBREQUEST}).
 * <p>
 * Web requests received by the Flux platform are logged. Connected client applications can route these requests using
 * this annotation or one of its specific variants, like {@link HandleGet}, {@link HandlePost}, etc.
 * </p>
 *
 * <p>
 * This annotation can be used directly to handle any method (GET, POST, etc.), or indirectly via more specific
 * annotations like {@link HandleGet}, {@link HandlePost}, {@link HandleSocketOpen}, etc.
 * </p>
 *
 * <p>
 * By default, a handler will return a result which is published to the {@code WebResponse} log. To suppress this
 * behavior, set {@code passive = true}.
 * </p>
 *
 * @see MessageType#WEBREQUEST
 * @see HandleGet
 * @see HandleSocketOpen
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@HandleMessage(MessageType.WEBREQUEST)
public @interface HandleWeb {
    /**
     * One or more path patterns this handler applies to (e.g. {@code /users}, {@code /accounts/*}). If empty, the
     * path is based on the {@link Path} annotation.
     */
    String[] value() default {};

    /**
     * If {@code true}, disables this handler during discovery.
     */
    boolean disabled() default false;

    /**
     * HTTP or WebSocket methods that this handler supports (e.g. GET, POST, WS_OPEN). Default is
     * {@link HttpRequestMethod#ANY}.
     */
    String[] method() default HttpRequestMethod.ANY;

    /**
     * If {@code true}, the handler will not publish a response to the {@code WebResponse} log.
     */
    boolean passive() default false;
}

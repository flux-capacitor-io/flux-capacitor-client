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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.MessageType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or constructor as a handler for internal metrics events ({@link MessageType#METRICS}).
 * <p>
 * Metrics messages are emitted by Flux client applications and the Flux platform to report on internal operations such
 * as read throughput, search activity, store position updates, or handler performance. These messages are valuable for
 * observability, debugging, auditing, and building custom monitoring or alerting systems.
 * </p>
 *
 * <p>
 * This annotation allows applications to process metrics events, optionally filtered by payload type. It is a concrete
 * specialization of {@link HandleMessage} for {@link MessageType#METRICS}.
 * </p>
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Monitoring how frequently specific projections are accessed</li>
 *   <li>Tracking search volume or performance for the document store</li>
 *   <li>Building dashboards with application-specific system insights</li>
 *   <li>Capturing store/read latency or error patterns</li>
 * </ul>
 *
 * @see HandleMessage
 * @see MessageType#METRICS
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@HandleMessage(MessageType.METRICS)
public @interface HandleMetrics {
    /**
     * If {@code true}, disables this handler during discovery.
     */
    boolean disabled() default false;

    /**
     * Restricts which payload types this handler may be invoked for.
     */
    Class<?>[] allowedClasses() default {};
}

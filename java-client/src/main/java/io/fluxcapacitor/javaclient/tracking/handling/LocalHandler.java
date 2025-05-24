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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a message handler method, class, or package as a **local handler**—one that is invoked immediately in the
 * publishing thread rather than asynchronously through tracking.
 *
 * <p>
 * Local handling is useful for:
 * <ul>
 *   <li>Requests that require <strong>ultra-low latency</strong> (e.g., frequent queries, projections, or quick lookups)</li>
 *   <li><strong>Lightweight processing</strong> that doesn’t need to be logged or retried</li>
 *   <li>Messages that should be handled <strong>exclusively in the current application</strong> instance</li>
 * </ul>
 *
 * <p>
 * When a handler is marked with {@code @LocalHandler}, messages it can handle will be processed **immediately after
 * publication** if a matching local handler exists. In most cases, this also means:
 * <ul>
 *   <li>The message is <strong>not persisted</strong> by default</li>
 *   <li><strong>Other non-local handlers will not see it</strong></li>
 * </ul>
 *
 * <h2>Example: Fast local query handling</h2>
 *
 * <pre>{@code
 * @LocalHandler
 * @HandleQuery
 * Product handle(GetProduct query) {
 *     return FluxCapacitor.search(Product.class).match(query.getProductId()).fetchFirst().orElse(null);
 * }
 * }</pre>
 *
 * <p>
 * Note: If you annotate a class or package with {@code @LocalHandler}, all handler methods within it are local by
 * default. To opt out for a specific method, use {@code @LocalHandler(false)} on that method.
 *
 * @see io.fluxcapacitor.common.MessageType
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface LocalHandler {

    /**
     * Whether the handler is local. This allows overriding the behavior declared at a higher level (e.g. disabling
     * local behavior for a specific method while the enclosing class is marked local).
     *
     * <p>
     * Defaults to {@code true}.
     */
    boolean value() default true;

    /**
     * Whether messages handled locally should also be **logged and published** to the event bus or event store.
     *
     * <p>
     * This is useful when:
     * <ul>
     *   <li>You want local processing for speed, but still need visibility or downstream processing</li>
     *   <li>Auditability is important (e.g. admin interfaces)</li>
     * </ul>
     *
     * <p>
     * Defaults to {@code false}.
     */
    boolean logMessage() default false;

    /**
     * Whether handling metrics (such as {@code HandleMessageEvent}) should be logged for this handler if monitoring is
     * enabled.
     *
     * <p>
     * Defaults to {@code false}.
     */
    boolean logMetrics() default false;

    /**
     * If {@code true}, this handler may also receive **externally published messages** (i.e. from other application
     * instances).
     *
     * <p>
     * Normally, a {@code @LocalHandler} is used for messages published and handled within the same instance only. Set
     * this to {@code true} if the handler should also participate in regular (remote) message tracking.
     *
     * <p>
     * Ignored if {@link #value()} is {@code false}.
     */
    boolean allowExternalMessages() default false;
}

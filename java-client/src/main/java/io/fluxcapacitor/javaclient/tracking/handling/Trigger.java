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
 * Injects the **triggering message** that caused the current message to be published or handled.
 *
 * <p>
 * This annotation is typically used in:
 * <ul>
 *   <li>{@link io.fluxcapacitor.javaclient.tracking.handling.HandleResult @HandleResult} handlers to access the original request</li>
 *   <li>{@link io.fluxcapacitor.javaclient.tracking.handling.HandleError @HandleError} handlers to inspect the command or query that failed</li>
 * </ul>
 *
 * <p>
 * It can be placed on:
 * <ul>
 *   <li>A handler <strong>method parameter</strong>: to inject the message or payload that triggered the current one</li>
 *   <li>A handler <strong>method itself</strong>: to restrict invocation to certain types of trigger messages</li>
 * </ul>
 *
 * <h2>Injection Behavior</h2>
 *
 * <ul>
 *   <li>The triggering message is injected if its structure matches the parameter type.</li>
 *   <li>Supported parameter types include:
 *     <ul>
 *       <li>The triggering payload type (e.g. {@code MyCommand})</li>
 *       <li>{@link io.fluxcapacitor.javaclient.common.Message Message}</li>
 *       <li>{@link io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage DeserializingMessage}</li>
 *     </ul>
 *   </li>
 *   <li>If no match is found, the handler is skipped.</li>
 * </ul>
 *
 * <h2>Filtering Options</h2>
 *
 * You can restrict the handler’s applicability using:
 *
 * <ul>
 *   <li>{@link #value()}: Only inject if the trigger’s payload type is assignable to one of the specified classes</li>
 *   <li>{@link #messageType()}: Only allow triggers of the given {@link io.fluxcapacitor.common.MessageType}</li>
 *   <li>{@link #consumer()}: Only match triggers from the specified publishing consumers</li>
 * </ul>
 *
 * <h2>Example: Handling a result with access to the triggering command</h2>
 *
 * <pre>{@code
 * @HandleResult
 * void handleResult(SuccessResponse result, @Trigger MyCommand originalCommand) {
 *     log.info("Command {} completed with result: {}", originalCommand.getId(), result);
 * }
 * }</pre>
 *
 * <h2>Example: Retrying failed commands using a consumer-specific trigger</h2>
 *
 * <pre>{@code
 * @HandleError
 * @Trigger(consumer = "my-app", messageType = MessageType.COMMAND)
 * void retryFailedCommand(MyCommand failedCommand) {
 *     FluxCapacitor.sendCommand(failedCommand);
 * }
 * }</pre>
 *
 * <h2>Advanced Use Case: Building a dynamic dead-letter queue</h2>
 *
 * <p>
 * Because trigger metadata is preserved, you can replay past failures even if no handler existed when the message
 * originally failed. For example, after discovering a bug days later, you can deploy a consumer that:
 * <ul>
 *   <li>Replays failed commands from the error log</li>
 *   <li>Uses {@code @Trigger} to inject and reissue them</li>
 *   <li>Recovers gracefully without needing manual inspection of logs</li>
 * </ul>
 *
 * @see io.fluxcapacitor.javaclient.tracking.handling.HandleResult
 * @see io.fluxcapacitor.javaclient.tracking.handling.HandleError
 */
@Target({ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Trigger {
    /**
     * Restricts the trigger message by payload type.
     * If left empty, any compatible payload will be injected.
     */
    Class<?>[] value() default {};

    /**
     * Restricts the trigger by its {@link io.fluxcapacitor.common.MessageType}.
     */
    MessageType[] messageType() default {};

    /**
     * Restricts the trigger to messages sent by specific consumer(s).
     */
    String[] consumer() default {};
}

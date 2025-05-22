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

package io.fluxcapacitor.common;

/**
 * Represents a delivery or completion guarantee for messages or state changes in Flux Capacitor.
 * <p>
 * Guarantees define how long an operation (e.g., sending a message or applying a state change) should wait before
 * considering itself complete.
 * <p>
 * These guarantees are especially relevant when sending messages through gateways such as {@code CommandGateway} or
 * when scheduling and persisting state changes to the Flux platform.
 */
public enum Guarantee {

    /**
     * No delivery guarantee is enforced.
     * <p>
     * Operations complete immediately after dispatching, without waiting for acknowledgment from Flux Capacitor or
     * local processing results.
     * <p>
     * Use this when performance is more important than confirmation, or when confirmation is handled separately.
     */
    NONE,

    /**
     * Guarantees that the message or action was sent successfully.
     * <p>
     * This includes:
     * <ul>
     *   <li>Local delivery to registered handlers</li>
     *   <li>Sending the message to the Flux Capacitor service</li>
     * </ul>
     * <p>
     * If a message is handled entirely locally, the operation completes once the local handling is done.
     * If the message is sent remotely, the operation waits for acknowledgment that it was sent successfully.
     */
    SENT,

    /**
     * Guarantees that the message or action was stored by Flux Capacitor.
     * <p>
     * The operation waits until a receipt or acknowledgment from Flux Capacitor confirms that the message or state
     * change has been durably stored.
     * <p>
     * This is the highest level of guarantee and ensures that the message won't be lost even if there is a failure
     * after dispatch.
     */
    STORED
}

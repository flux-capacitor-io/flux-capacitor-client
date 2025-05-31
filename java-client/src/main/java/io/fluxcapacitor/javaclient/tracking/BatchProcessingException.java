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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.Getter;

/**
 * Exception thrown during message batch processing to intentionally halt tracking after a specific message.
 * <p>
 * This exception provides a clean way to interrupt processing without treating the batch as failed. When thrown,
 * the tracker stops consuming messages and commits its position up to—but not including—the message at the specified
 * {@code messageIndex}.
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li>The {@code messageIndex} refers to the index of the **message that caused the halt** within the current batch.</li>
 *   <li>The tracker will commit all messages prior to this index.</li>
 *   <li>Remaining messages in the batch will be retried when tracking resumes.</li>
 * </ul>
 *
 * <h2>Auto-Indexing</h2>
 * <p>
 * If no index is explicitly provided, the exception will attempt to resolve the current message index automatically
 * via {@link io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage#getOptionally()}. This makes it
 * convenient to throw from inside a handler or interceptor.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Gracefully halting tracking due to domain constraints or environmental factors</li>
 *   <li>Aborting a batch without marking it as failed (e.g., for safe reprocessing)</li>
 *   <li>Stopping execution when continuation might result in corruption or inconsistency</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @HandleEvent
 * public void on(CriticalSystemEvent event) {
 *     if (systemInUnsafeState()) {
 *         throw new BatchProcessingException("Unsafe state detected. Halting tracker.");
 *     }
 * }
 * }</pre>
 *
 * <p>The tracker will commit its position up to the last successfully processed message before the one that triggered the exception.
 *
 * @see io.fluxcapacitor.javaclient.tracking.Tracker
 * @see io.fluxcapacitor.javaclient.tracking.ErrorHandler
 */
@Getter
public class BatchProcessingException extends RuntimeException {

    /**
     * Index of the message within the batch that caused the interruption.
     */
    private final Long messageIndex;

    /**
     * Constructs the exception using the index of the currently handled message, if available.
     */
    public BatchProcessingException() {
        this(DeserializingMessage.getOptionally().map(DeserializingMessage::getIndex).orElse(null));
    }

    /**
     * Constructs the exception with a message and automatically determines the current message index.
     */
    public BatchProcessingException(String message) {
        this(message, DeserializingMessage.getOptionally().map(DeserializingMessage::getIndex).orElse(null));
    }

    /**
     * Constructs the exception with a message, cause, and automatically determined message index.
     */
    public BatchProcessingException(String message, Throwable cause) {
        this(message, cause, DeserializingMessage.getOptionally().map(DeserializingMessage::getIndex).orElse(null));
    }

    /**
     * Constructs the exception with a specified message index.
     */
    public BatchProcessingException(Long messageIndex) {
        this.messageIndex = messageIndex;
    }

    /**
     * Constructs the exception with a message and a specified message index.
     */
    public BatchProcessingException(String message, Long messageIndex) {
        super(message);
        this.messageIndex = messageIndex;
    }

    /**
     * Constructs the exception with a message, cause, and message index.
     */
    public BatchProcessingException(String message, Throwable cause, Long messageIndex) {
        super(message, cause);
        this.messageIndex = messageIndex;
    }
}

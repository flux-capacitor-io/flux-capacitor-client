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

import io.fluxcapacitor.common.handling.MessageFilter;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static io.fluxcapacitor.javaclient.common.ClientUtils.memoize;

/**
 * A {@link MessageFilter} that routes {@link DeserializingMessage} instances to methods annotated with
 * {@link HandleDocument}, based on the message's topic.
 *
 * <p>This filter checks if the target handler method is annotated with {@code @HandleDocument}, and if so,
 * whether the resolved topic from the annotation matches the topic of the incoming message. If both conditions are met,
 * the message will be considered eligible for handling by the method.
 *
 * <p>The resolved topic is derived using {@link ClientUtils#getTopic(HandleDocument, Executable)},
 * which supports dynamic resolution based on annotation configuration and handler context.
 *
 * <p>Annotation lookups are memoized for performance.
 *
 * @see HandleDocument
 * @see DeserializingMessage
 * @see ClientUtils#getTopic(HandleDocument, Executable)
 */
public class HandleDocumentFilter implements MessageFilter<DeserializingMessage> {

    Function<Executable, Optional<HandleDocument>> handleCustomFunction =
            memoize(e -> ReflectionUtils.getAnnotation(e, HandleDocument.class));

    @Override
    public boolean test(DeserializingMessage message, Executable executable,
                        Class<? extends Annotation> handlerAnnotation) {
        return handleCustomFunction.apply(executable).map(
                        handleDocument -> ClientUtils.getTopic(handleDocument, executable))
                .map(handlerCollection -> Objects.equals(message.getTopic(), handlerCollection))
                .orElse(false);
    }
}

/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

import io.fluxcapacitor.common.AnnotationUtils;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.configuration.ConfigurationException;
import io.fluxcapacitor.javaclient.web.WebMethod;
import io.fluxcapacitor.javaclient.web.handling.HandleWebRequest;
import io.fluxcapacitor.javaclient.web.handling.HandleWebResponse;
import lombok.RequiredArgsConstructor;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static io.fluxcapacitor.common.AnnotationUtils.invokeAnnotationMethod;
import static io.fluxcapacitor.common.handling.HandlerInspector.hasHandlerMethods;
import static io.fluxcapacitor.javaclient.web.WebMethod.UNKNOWN;
import static java.util.Optional.ofNullable;

@RequiredArgsConstructor
public class DefaultHandlerFactory implements HandlerFactory {
    private final MessageType messageType;
    private final HandlerInterceptor handlerInterceptor;
    private final List<ParameterResolver<? super DeserializingMessage>> parameterResolvers;

    @Override
    public Optional<Handler<DeserializingMessage>> createHandler(
            Object target, String consumer, HandlerConfiguration<DeserializingMessage> handlerConfiguration) {
        Class<? extends Annotation> methodAnnotation = getHandlerAnnotation(messageType);
        if (hasHandlerMethods(target.getClass(), methodAnnotation, handlerConfiguration)) {
            return Optional.of(handlerInterceptor.wrap(
                    HandlerInspector.createHandler(target, methodAnnotation, parameterResolvers,
                            handlerConfiguration.toBuilder().annotationFilter(
                                    DefaultHandlerFactory::getHandlerAnnotationFilter).build()),
                    consumer));
        }
        return Optional.empty();
    }

    private static Class<? extends Annotation> getHandlerAnnotation(MessageType messageType) {
        switch (messageType) {
            case COMMAND:
                return HandleCommand.class;
            case EVENT:
                return HandleEvent.class;
            case NOTIFICATION:
                return HandleNotification.class;
            case QUERY:
                return HandleQuery.class;
            case RESULT:
                return HandleResult.class;
            case ERROR:
                return HandleError.class;
            case SCHEDULE:
                return HandleSchedule.class;
            case METRICS:
                return HandleMetrics.class;
            case WEBREQUEST:
                return HandleWebRequest.class;
            case WEBRESPONSE:
                return HandleWebResponse.class;
            default:
                throw new ConfigurationException(String.format("Unrecognized type: %s", messageType));
        }
    }

    private static Predicate<DeserializingMessage> getHandlerAnnotationFilter(Annotation annotation) {
        if (AnnotationUtils.isAssignableFrom(HandleWebRequest.class, annotation.annotationType())) {
            WebMethod method = (WebMethod) invokeAnnotationMethod(annotation, "method");
            String path = (String) invokeAnnotationMethod(annotation, "value");
            if(method==null || path == null){
                return m -> false;
            }
            Pattern pathPattern = Pattern.compile(path);
            Predicate<DeserializingMessage> pathFilter = m -> ofNullable(m.getMetadata().get("path"))
                    .map(p -> p.split("\\?")[0])
                    .map(p -> pathPattern.matcher(p).matches()).orElse(false);
            return method == UNKNOWN ? pathFilter : pathFilter.and(m ->
                    method == m.getMetadata().get("method", WebMethod.class));
        }
        return m -> true;
    }
}

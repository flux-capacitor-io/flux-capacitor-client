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

import io.fluxcapacitor.javaclient.modeling.HandlerRepository;
import io.fluxcapacitor.javaclient.persisting.search.Searchable;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be placed on stateful message handlers. If this annotation is present it is possible to 'apply'
 * messages like events on stored handler instances, or automatically store handlers.
 * <p>
 * Messages are associated with stored handlers using {@link Association associations}.
 * <p>
 * Handlers are persisted to a {@link HandlerRepository}. By default, a repository backed by the
 * {@link io.fluxcapacitor.javaclient.persisting.search.DocumentStore} is used. An identifier for new handlers is
 * automatically generated unless a property of the handler is annotated with
 * {@link io.fluxcapacitor.javaclient.modeling.EntityId}, in which case the property is used to determine the id.
 *
 * @see Association
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Searchable
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public @interface Stateful {

    /**
     * Returns the name of the collection in which the handler should be stored. Defaults to the simple name of Handler
     * class.
     */
    String collection() default "";

    /**
     * Returns the name of property on the handler that contains a timestamp associated with the handler. This may be
     * useful in case the handlers need to e.g. be presented in an overview.
     */
    String timestampPath() default "";

    /**
     * Returns the name of property on the handler that contains an end timestamp associated with the handler. This may
     * be useful in case the handlers need to e.g. be presented in an overview.
     */
    String endPath() default "";

    /**
     * Determines whether changes to stateful handlers should be committed at end of the current message batch of the
     * current tracker or at the end of the current message.
     */
    boolean commitInBatch() default false;
}

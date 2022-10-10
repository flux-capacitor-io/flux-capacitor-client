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

package io.fluxcapacitor.javaclient.tracking;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

import static java.time.temporal.ChronoUnit.SECONDS;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface Consumer {
    String name() default "";

    int threads() default 1;

    int maxFetchSize() default 1024;

    long maxWaitDuration() default 60;

    ChronoUnit durationUnit() default SECONDS;

    Class<? extends BatchInterceptor>[] batchInterceptors() default {};

    boolean retryOnError() default false;

    boolean stopAfterError() default false;

    Class<? extends ErrorHandler> customErrorHandler() default ErrorHandler.class;

    boolean ignoreMessageTarget() default false;

    boolean ignoreSegment() default false;

    boolean singleTracker() default false;

    boolean exclusive() default true;

    boolean passive() default false;

    long minIndex() default -1L;

    long maxIndexExclusive() default -1L;

    String typeFilter() default "";
}

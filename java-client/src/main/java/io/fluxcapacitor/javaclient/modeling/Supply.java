/*
 * Copyright (c) 2016-2018 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.modeling;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods on commands or events that are able to supply child entities or other context before event
 * sourcing or command validation etc. Those child entities can then be used as input parameters in e.g. {@link
 * AssertLegal} and {@link Apply} methods.
 * <p>
 * Annotated methods should contain at least one parameter. It is possible to define the aggregate or other child
 * entities as parameter so long as these have been supplied elsewhere. Visibility level of methods is not important,
 * i.e. supply methods may be private.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Supply {
}

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

package io.fluxcapacitor.javaclient.tracking.handling.validation;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import lombok.Getter;

import java.beans.ConstructorProperties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Indicates that a request (typically a command or query) contains one or more field-level violations that prevent
 * further processing.
 * <p>
 * The {@link #violations} set contains a sorted list of human-readable violation messages.
 * </p>
 */
@Getter
public class ValidationException extends FunctionalException {
    private final SortedSet<String> violations;

    @ConstructorProperties({"message", "violations"})
    public ValidationException(String message, Set<String> violations) {
        super(message);
        this.violations = new TreeSet<>(violations);
    }
}

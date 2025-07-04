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

package io.fluxcapacitor.javaclient.publishing;

import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

/**
 * Default implementation of the {@link QueryGateway} interface.
 * <p>
 * This class delegates all operations defined in the {@link QueryGateway} interface to an underlying
 * {@link GenericGateway} instance.
 *
 * @see QueryGateway
 * @see GenericGateway
 */
@AllArgsConstructor
public class DefaultQueryGateway implements QueryGateway {
    @Delegate
    private final GenericGateway delegate;
}

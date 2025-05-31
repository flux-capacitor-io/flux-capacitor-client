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
 * Default implementation of the {@link CommandGateway} interface.
 * <p>
 * This class delegates all functionality to an underlying {@link GenericGateway} instance, enabling the use of command
 * gateway methods while leveraging the generic gateway's capabilities.
 *
 * @see CommandGateway
 * @see GenericGateway
 */
@AllArgsConstructor
public class DefaultCommandGateway implements CommandGateway {
    @Delegate
    private final GenericGateway delegate;
}

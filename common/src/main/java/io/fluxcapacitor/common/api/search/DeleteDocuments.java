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

package io.fluxcapacitor.common.api.search;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Command;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Command to delete documents from the search store based on a search query.
 * <p>
 * This command allows for bulk deletion of documents that match a specified {@link SearchQuery},
 * across one or more collections. The deletion respects the provided {@link Guarantee}.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class DeleteDocuments extends Command {

    /**
     * The query describing which documents should be deleted.
     */
    SearchQuery query;

    /**
     * The delivery/storage guarantee to apply for this deletion operation.
     */
    Guarantee guarantee;
}

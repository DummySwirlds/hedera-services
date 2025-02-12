/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.metrics.platform;

import com.swirlds.common.platform.NodeId;
import com.swirlds.metrics.api.snapshot.Snapshot;
import java.util.Collection;
import java.util.Objects;

/**
 * Represents a snapshot event that contains a collection of snapshots.
 * @param nodeId the node identifier
 * @param snapshots the collection of snapshots
 */
public record SnapshotEvent(NodeId nodeId, Collection<Snapshot> snapshots) {

    /**
     * @throws NullPointerException in case {@code snapshots} parameter is {@code null}
     */
    public SnapshotEvent {
        Objects.requireNonNull(snapshots, "snapshots must not be null");
    }
}

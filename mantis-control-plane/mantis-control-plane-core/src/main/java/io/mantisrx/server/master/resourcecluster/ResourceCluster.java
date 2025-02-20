/*
 * Copyright 2022 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.mantisrx.server.master.resourcecluster;

import io.mantisrx.common.Ack;
import io.mantisrx.runtime.MachineDefinition;
import io.mantisrx.server.core.domain.WorkerId;
import io.mantisrx.server.worker.TaskExecutorGateway;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * Abstraction to deal with all interactions with the resource cluster such as
 * 1). listing the set of task executors registered
 * 2). listing the set of task executors available
 * 3). listing the set of task executors busy
 * 4). get the current state of a task executor
 * 5). get the current state of the system
 * 6). assign a task executor for a given worker
 */
public interface ResourceCluster extends ResourceClusterGateway {
    /**
     * API that gets invoked when the resource cluster migrates from one machine to another and needs to be initialized.
     *
     * @param taskExecutorID taskExecutorID that was originally running the worker
     * @param workerId workerID of the task that being run on the task executor
     * @return Ack when the initialization is done
     */
    CompletableFuture<Ack> initializeTaskExecutor(TaskExecutorID taskExecutorID, WorkerId workerId);

    CompletableFuture<List<TaskExecutorID>> getRegisteredTaskExecutors();

    CompletableFuture<List<TaskExecutorID>> getAvailableTaskExecutors();

    CompletableFuture<List<TaskExecutorID>> getBusyTaskExecutors();

    CompletableFuture<List<TaskExecutorID>> getUnregisteredTaskExecutors();

    CompletableFuture<ResourceOverview> resourceOverview();

    /**
     * Can throw {@link NoResourceAvailableException} wrapped within the CompletableFuture in case there
     * are no task executors.
     *
     * @param machineDefinition machine definition that's requested for the worker
     * @param workerId worker id of the task that's going to run on the node.
     * @return task executor assigned for the particular task.
     */
    CompletableFuture<TaskExecutorID> getTaskExecutorFor(MachineDefinition machineDefinition, WorkerId workerId);

    CompletableFuture<TaskExecutorGateway> getTaskExecutorGateway(TaskExecutorID taskExecutorID);

    CompletableFuture<TaskExecutorRegistration> getTaskExecutorInfo(String hostName);

    CompletableFuture<TaskExecutorRegistration> getTaskExecutorInfo(TaskExecutorID taskExecutorID);

    CompletableFuture<TaskExecutorStatus> getTaskExecutorState(TaskExecutorID taskExecutorID);

    class NoResourceAvailableException extends Exception {

        public NoResourceAvailableException(String message) {
            super(message);
        }
    }

    @Value
    class ResourceOverview {
        long numRegisteredTaskExecutors;
        long numAvailableTaskExecutors;
        long numOccupiedTaskExecutors;
        long numAssignedTaskExecutors;
    }

    @Value
    class TaskExecutorStatus {
        TaskExecutorRegistration registration;
        boolean registered;
        boolean runningTask;
        boolean assignedTask;
        @Nullable
        WorkerId workerId;
        long lastHeartbeatInMs;
    }
}

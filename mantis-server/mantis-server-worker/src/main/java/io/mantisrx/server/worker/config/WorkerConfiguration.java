/*
 * Copyright 2019 Netflix, Inc.
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

package io.mantisrx.server.worker.config;

import io.mantisrx.server.core.CoreConfiguration;
import java.io.File;
import java.net.URI;
import org.apache.flink.api.common.time.Time;
import org.skife.config.Config;
import org.skife.config.Default;
import org.skife.config.DefaultNull;

public interface WorkerConfiguration extends CoreConfiguration {

    // Old configurations for mesos
    @Config("mantis.agent.mesos.slave.port")
    @Default("5051")
    int getMesosSlavePort();

    // ------------------------------------------------------------------------
    //  Task Executor machine related configurations
    // ------------------------------------------------------------------------
    @Config("mantis.taskexecutor.id")
    @DefaultNull
    String getTaskExecutorId();

    @Config("mantis.taskexecutor.cluster-id")
    @Default("DEFAULT_CLUSTER")
    String getClusterId();

    @Config("mantis.taskexecutor.ports.metrics")
    @Default("5051")
    int getMetricsPort();

    @Config("mantis.taskexecutor.ports.debug")
    @Default("5052")
    int getDebugPort();

    @Config("mantis.taskexecutor.ports.console")
    @Default("5053")
    int getConsolePort();

    @Config("mantis.taskexecutor.ports.custom")
    @Default("5054")
    int getCustomPort();

    @Config("mantis.taskexecutor.ports.sink")
    @Default("5055")
    int getSinkPort();

    // ------------------------------------------------------------------------
    //  heartbeat connection related configurations
    // ------------------------------------------------------------------------
    @Config("mantis.taskexecutor.heartbeats.interval")
    @Default("10000")
    int heartbeatInternalInMs();

    @Config("mantis.taskexecutor.heartbeats.tolerable-consecutive-heartbeat-failures")
    @Default("3")
    int getTolerableConsecutiveHeartbeatFailures();

    @Config("mantis.taskexecutor.heartbeats.timeout.ms")
    @Default("5000")
    int heartbeatTimeoutMs();

    default Time getHeartbeatTimeout() {
        return Time.milliseconds(heartbeatTimeoutMs());
    }

    default Time getHeartbeatInterval() {
        return Time.milliseconds(heartbeatInternalInMs());
    }

    // ------------------------------------------------------------------------
    //  RPC related configurations
    // ------------------------------------------------------------------------
    @Config("mantis.taskexecutor.rpc.external-address")
    @Default("${EC2_LOCAL_IPV4}")
    String getExternalAddress();

    @Config("mantis.taskexecutor.rpc.port-range")
    @Default("")
    String getExternalPortRange();

    @Config("mantis.taskexecutor.rpc.bind-address")
    @DefaultNull
    String getBindAddress();

    @Config("mantis.taskexecutor.rpc.bind-port")
    @DefaultNull
    Integer getBindPort();

    // ------------------------------------------------------------------------
    //  BlobStore related configurations
    // ------------------------------------------------------------------------
    @Config("mantis.taskexecutor.blob-store.storage-dir")
    @DefaultNull
    URI getBlobStoreArtifactDir();

    @Config("mantis.taskexecutor.blob-store.local-cache")
    @DefaultNull
    File getLocalStorageDir();
}

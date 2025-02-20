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
package io.mantisrx.master.resourcecluster;

import static akka.pattern.Patterns.pipe;

import akka.actor.AbstractActorWithTimers;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import io.mantisrx.master.jobcluster.proto.BaseResponse.ResponseCode;
import io.mantisrx.master.resourcecluster.proto.GetResourceClusterSpecRequest;
import io.mantisrx.master.resourcecluster.proto.ListResourceClusterRequest;
import io.mantisrx.master.resourcecluster.proto.ProvisionResourceClusterRequest;
import io.mantisrx.master.resourcecluster.proto.ResourceClusterAPIProto.DeleteResourceClusterRequest;
import io.mantisrx.master.resourcecluster.proto.ResourceClusterAPIProto.DeleteResourceClusterResponse;
import io.mantisrx.master.resourcecluster.proto.ResourceClusterAPIProto.GetResourceClusterResponse;
import io.mantisrx.master.resourcecluster.proto.ResourceClusterAPIProto.ListResourceClustersResponse;
import io.mantisrx.master.resourcecluster.proto.ResourceClusterProvisionSubmissionResponse;
import io.mantisrx.master.resourcecluster.proto.ScaleResourceRequest;
import io.mantisrx.master.resourcecluster.resourceprovider.InMemoryOnlyResourceClusterStorageProvider;
import io.mantisrx.master.resourcecluster.resourceprovider.ResourceClusterProvider;
import io.mantisrx.master.resourcecluster.resourceprovider.ResourceClusterStorageProvider;
import io.mantisrx.master.resourcecluster.writable.ResourceClusterSpecWritable;
import io.mantisrx.shaded.com.google.common.annotations.VisibleForTesting;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * This actor is responsible to translate requests for resource cluster related operations from API server and other
 * actors to binded resource cluster provider implementation.
 */
@Slf4j
public class ResourceClustersHostManagerActor extends AbstractActorWithTimers {

    @VisibleForTesting
    static Props props(
            final ResourceClusterProvider resourceClusterProvider) {
        return Props.create(
                ResourceClustersHostManagerActor.class,
                resourceClusterProvider,
                new InMemoryOnlyResourceClusterStorageProvider());
    }

    public static Props props(
            final ResourceClusterProvider resourceClusterProvider,
            final ResourceClusterStorageProvider resourceStorageProvider) {
        // TODO(andyz): investigate atlas metered-mailbox.
        return Props.create(ResourceClustersHostManagerActor.class, resourceClusterProvider, resourceStorageProvider);
    }

    private final ResourceClusterProvider resourceClusterProvider;
    private final ResourceClusterStorageProvider resourceClusterStorageProvider;

    public ResourceClustersHostManagerActor(
            final ResourceClusterProvider resourceClusterProvider,
            final ResourceClusterStorageProvider resourceStorageProvider) {
        this.resourceClusterProvider = resourceClusterProvider;
        this.resourceClusterStorageProvider = resourceStorageProvider;
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(ProvisionResourceClusterRequest.class, this::onProvisionResourceClusterRequest)
                .match(ScaleResourceRequest.class, this::onScaleResourceClusterRequest)
                .match(ListResourceClusterRequest.class, this::onListResourceClusterRequest)
                .match(GetResourceClusterSpecRequest.class, this::onGetResourceClusterSpecRequest)
                .match(ResourceClusterProvisionSubmissionResponse.class, this::onResourceClusterProvisionResponse)
                .match(DeleteResourceClusterRequest.class, this::onDeleteResourceCluster)
                .build();
    }

    private void onDeleteResourceCluster(DeleteResourceClusterRequest req) {
        /**
         * Proper cluster deletion requires handling various cleanups e.g.:
         * * Migrate existing jobs.
         * * Un-provision cluster resources (nodes, network, storage e.g.).
         * * Update internal tracking state and persistent data.
         * For now this API will only serve the persistence layer update.
         */

        pipe(this.resourceClusterStorageProvider.deregisterCluster(req.getClusterId())
                .thenApply(clustersW ->
                    DeleteResourceClusterResponse.builder()
                        .responseCode(ResponseCode.SUCCESS)
                        .build())
                .exceptionally(err ->
                    DeleteResourceClusterResponse.builder()
                        .message(err.getMessage())
                        .responseCode(ResponseCode.SERVER_ERROR).build()),
            getContext().dispatcher())
            .to(getSender());
    }

    private void onResourceClusterProvisionResponse(ResourceClusterProvisionSubmissionResponse resp) {
        this.resourceClusterProvider.getResponseHandler().handleProvisionResponse(resp);
    }

    private void onListResourceClusterRequest(ListResourceClusterRequest req) {
        pipe(this.resourceClusterStorageProvider.getRegisteredResourceClustersWritable()
                .thenApply(clustersW ->
                        ListResourceClustersResponse.builder()
                                .responseCode(ResponseCode.SUCCESS)
                                .registeredResourceClusters(clustersW.getClusters().entrySet().stream().map(
                                        kv -> ListResourceClustersResponse.RegisteredResourceCluster.builder()
                                                .id(kv.getValue().getClusterId())
                                                .version(kv.getValue().getVersion())
                                                .build())
                                        .collect(Collectors.toList()))
                                .build()
                ).exceptionally(err ->
                                ListResourceClustersResponse.builder()
                                        .message(err.getMessage())
                                        .responseCode(ResponseCode.SERVER_ERROR).build()),
                getContext().dispatcher())
                .to(getSender());
    }

    private void onGetResourceClusterSpecRequest(GetResourceClusterSpecRequest req) {
        pipe(this.resourceClusterStorageProvider.getResourceClusterSpecWritable(req.getId())
                        .thenApply(specW -> {
                            if (specW == null) {
                                return GetResourceClusterResponse.builder()
                                        .responseCode(ResponseCode.CLIENT_ERROR_NOT_FOUND)
                                        .build();
                            }
                            return GetResourceClusterResponse.builder()
                                    .responseCode(ResponseCode.SUCCESS)
                                    .clusterSpec(specW.getClusterSpec())
                                    .build();
                        })
                        .exceptionally(err ->
                                GetResourceClusterResponse.builder()
                                        .responseCode(ResponseCode.SERVER_ERROR)
                                        .message(err.getMessage())
                                        .build()),
                getContext().dispatcher())
                .to(getSender());
    }

    private void onProvisionResourceClusterRequest(ProvisionResourceClusterRequest req) {
        /*
        For a provision request, the following steps will be taken:
        1. Persist the cluster request with spec to the resource storage provider.
        2. Once persisted, reply to sender (e.g. http server route) to confirm the accepted request.
        3. Queue the long-running provision task via resource cluster provider and register callback to self.
        4. Handle provision callback and error handling.
            (only logging for now as agent registration will happen directly inside agent).
         */
        log.info("Entering onProvisionResourceClusterRequest: " + req);

        ResourceClusterSpecWritable specWritable = ResourceClusterSpecWritable.builder()
                .clusterSpec(req.getClusterSpec())
                .version("")
                .id(req.getClusterId())
                .build();

        // Cluster spec is returned for API request.
        CompletionStage<GetResourceClusterResponse> updateSpecToStoreFut =
                this.resourceClusterStorageProvider.registerAndUpdateClusterSpec(specWritable)
                        .thenApply(specW -> GetResourceClusterResponse.builder()
                                        .responseCode(ResponseCode.SUCCESS)
                                        .clusterSpec(specW.getClusterSpec())
                                        .build())
                        .exceptionally(err ->
                                GetResourceClusterResponse.builder()
                                .responseCode(ResponseCode.SERVER_ERROR)
                                .message(err.getMessage())
                                .build());
        pipe(updateSpecToStoreFut, getContext().dispatcher()).to(getSender());
        log.debug("[Pipe finish] storing cluster spec.");

        // Provision response is directed back to this actor to handle its submission result.
        CompletionStage<ResourceClusterProvisionSubmissionResponse> provisionFut =
                updateSpecToStoreFut
                        .thenCompose(resp -> {
                            if (resp.responseCode.equals(ResponseCode.SUCCESS)) {
                                return this.resourceClusterProvider.provisionClusterIfNotPresent(req);
                            }
                            return CompletableFuture.completedFuture(
                                ResourceClusterProvisionSubmissionResponse.builder().response(resp.message).build());
                        })
                        .exceptionally(err -> ResourceClusterProvisionSubmissionResponse.builder().error(err).build());
        pipe(provisionFut, getContext().dispatcher()).to(getSelf());
        log.debug("[Pipe finish 2]: returned provision fut.");
    }

    private void onScaleResourceClusterRequest(ScaleResourceRequest req) {
        log.info("Entering onScaleResourceClusterRequest: " + req);
        pipe(this.resourceClusterProvider.scaleResource(req), getContext().dispatcher()).to(getSender());
    }
}

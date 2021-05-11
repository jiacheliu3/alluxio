/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio.worker.block;

import alluxio.AbstractMasterClient;
import alluxio.Constants;
import alluxio.conf.PropertyKey;
import alluxio.grpc.BlockHeartbeatPOptions;
import alluxio.grpc.BlockHeartbeatPRequest;
import alluxio.grpc.BlockIdList;
import alluxio.grpc.BlockMasterWorkerServiceGrpc;
import alluxio.grpc.BlockStoreLocationProto;
import alluxio.grpc.Command;
import alluxio.grpc.CommitBlockInUfsPRequest;
import alluxio.grpc.CommitBlockPRequest;
import alluxio.grpc.ConfigProperty;
import alluxio.grpc.GetWorkerIdPRequest;
import alluxio.grpc.LocationBlockIdListEntry;
import alluxio.grpc.Metric;
import alluxio.grpc.RegisterWorkerPOptions;
import alluxio.grpc.RegisterWorkerPRequest;
import alluxio.grpc.RegisterWorkerPResponse;
import alluxio.grpc.ServiceType;
import alluxio.grpc.StorageList;
import alluxio.master.MasterClientContext;
import alluxio.grpc.GrpcUtils;
import alluxio.wire.WorkerNetAddress;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A wrapper for the gRPC client to interact with the block master, used by alluxio worker.
 * <p/>
 */
@ThreadSafe
public class BlockMasterClient extends AbstractMasterClient {
  private static final Logger LOG = LoggerFactory.getLogger(BlockMasterClient.class);
  private BlockMasterWorkerServiceGrpc.BlockMasterWorkerServiceBlockingStub mClient = null;
  private BlockMasterWorkerServiceGrpc.BlockMasterWorkerServiceStub mStreamClient = null;

  /**
   * Creates a new instance of {@link BlockMasterClient} for the worker.
   *
   * @param conf master client configuration
   */
  public BlockMasterClient(MasterClientContext conf) {
    super(conf);
  }

  @Override
  protected ServiceType getRemoteServiceType() {
    return ServiceType.BLOCK_MASTER_WORKER_SERVICE;
  }

  @Override
  protected String getServiceName() {
    return Constants.BLOCK_MASTER_WORKER_SERVICE_NAME;
  }

  @Override
  protected long getServiceVersion() {
    return Constants.BLOCK_MASTER_WORKER_SERVICE_VERSION;
  }

  @Override
  protected void afterConnect() throws IOException {
    mClient = BlockMasterWorkerServiceGrpc.newBlockingStub(mChannel);
  }

  /**
   * Commits a block on a worker.
   *
   * @param workerId the worker id committing the block
   * @param usedBytesOnTier the amount of used bytes on the tier the block is committing to
   * @param tierAlias the alias of the tier the block is being committed to
   * @param mediumType the medium type the block is being committed to
   * @param blockId the block id being committed
   * @param length the length of the block being committed
   */
  public void commitBlock(final long workerId, final long usedBytesOnTier,
      final String tierAlias, final String mediumType,
      final long blockId, final long length) throws IOException {
    retryRPC(() -> {
      CommitBlockPRequest request =
          CommitBlockPRequest.newBuilder().setWorkerId(workerId).setUsedBytesOnTier(usedBytesOnTier)
              .setTierAlias(tierAlias).setMediumType(mediumType)
              .setBlockId(blockId).setLength(length).build();
      mClient.commitBlock(request);
      return null;
    }, LOG, "CommitBlock",
        "workerId=%d,usedBytesOnTier=%d,tierAlias=%s,mediumType=%s,blockId=%d,length=%d",
        workerId, usedBytesOnTier, tierAlias, mediumType, blockId, length);
  }

  /**
   * Commits a block in Ufs.
   *
   * @param blockId the block id being committed
   * @param length the length of the block being committed
   */
  public void commitBlockInUfs(final long blockId, final long length)
      throws IOException {
    retryRPC(() -> {
      CommitBlockInUfsPRequest request =
          CommitBlockInUfsPRequest.newBuilder().setBlockId(blockId).setLength(length).build();
      mClient.commitBlockInUfs(request);
      return null;
    }, LOG, "CommitBlockInUfs", "blockId=%d,length=%d", blockId, length);
  }

  /**
   * Returns a worker id for a workers net address.
   *
   * @param address the net address to get a worker id for
   * @return a worker id
   */
  public long getId(final WorkerNetAddress address) throws IOException {
    return retryRPC(() -> {
      GetWorkerIdPRequest request =
          GetWorkerIdPRequest.newBuilder().setWorkerNetAddress(GrpcUtils.toProto(address)).build();
      return mClient.getWorkerId(request).getWorkerId();
    }, LOG, "GetId", "address=%s", address);
  }

  /**
   * Converts the block list map to a proto list.
   * Because the list is flattened from a map, in the list no two {@link LocationBlockIdListEntry}
   * instances shall have the same {@link BlockStoreLocationProto}.
   * The uniqueness of {@link BlockStoreLocationProto} is determined by tier alias and medium type.
   * That means directories with the same tier alias and medium type will be merged into the same
   * {@link LocationBlockIdListEntry}.
   *
   * @param blockListOnLocation a map from block location to the block list
   * @return a flattened and deduplicated list
   */
  @VisibleForTesting
  List<LocationBlockIdListEntry> convertBlockListMapToProto(
          Map<BlockStoreLocation, List<Long>> blockListOnLocation) {
    final List<LocationBlockIdListEntry> entryList = new ArrayList<>();

    Map<BlockStoreLocationProto, List<Long>> tierToBlocks = new HashMap<>();
    for (Map.Entry<BlockStoreLocation, List<Long>> entry : blockListOnLocation.entrySet()) {
      BlockStoreLocation loc = entry.getKey();
      BlockStoreLocationProto locationProto = BlockStoreLocationProto.newBuilder()
          .setTierAlias(loc.tierAlias())
          .setMediumType(loc.mediumType())
          .build();
      if (tierToBlocks.containsKey(locationProto)) {
        tierToBlocks.get(locationProto).addAll(entry.getValue());
      } else {
        List<Long> blockList = new ArrayList<>(entry.getValue());
        tierToBlocks.put(locationProto, blockList);
      }
    }
    for (Map.Entry<BlockStoreLocationProto, List<Long>> entry : tierToBlocks.entrySet()) {
      BlockIdList blockIdList = BlockIdList.newBuilder().addAllBlockId(entry.getValue()).build();
      LocationBlockIdListEntry listEntry = LocationBlockIdListEntry.newBuilder()
          .setKey(entry.getKey()).setValue(blockIdList).build();
      entryList.add(listEntry);
    }

    return entryList;
  }

  /**
   * The method the worker should periodically execute to heartbeat back to the master.
   *
   * @param workerId the worker id
   * @param capacityBytesOnTiers a mapping from storage tier alias to capacity bytes
   * @param usedBytesOnTiers a mapping from storage tier alias to used bytes
   * @param removedBlocks a list of block removed from this worker
   * @param addedBlocks a mapping from storage tier alias to added blocks
   * @param lostStorage a mapping from storage tier alias to a list of lost storage paths
   * @param metrics a list of worker metrics
   * @return an optional command for the worker to execute
   */
  public synchronized Command heartbeat(final long workerId,
      final Map<String, Long> capacityBytesOnTiers, final Map<String, Long> usedBytesOnTiers,
      final List<Long> removedBlocks, final Map<BlockStoreLocation, List<Long>> addedBlocks,
      final Map<String, List<String>> lostStorage, final List<Metric> metrics)
      throws IOException {
    final BlockHeartbeatPOptions options = BlockHeartbeatPOptions.newBuilder()
        .addAllMetrics(metrics).putAllCapacityBytesOnTiers(capacityBytesOnTiers).build();

    final List<LocationBlockIdListEntry> entryList = convertBlockListMapToProto(addedBlocks);

    final Map<String, StorageList> lostStorageMap = lostStorage.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey,
            e -> StorageList.newBuilder().addAllStorage(e.getValue()).build()));

    final BlockHeartbeatPRequest request = BlockHeartbeatPRequest.newBuilder().setWorkerId(workerId)
        .putAllUsedBytesOnTiers(usedBytesOnTiers).addAllRemovedBlockIds(removedBlocks)
        .addAllAddedBlocks(entryList).setOptions(options)
        .putAllLostStorage(lostStorageMap).build();

    return retryRPC(() -> mClient.withDeadlineAfter(mContext.getClusterConf()
        .getMs(PropertyKey.WORKER_MASTER_PERIODICAL_RPC_TIMEOUT), TimeUnit.MILLISECONDS)
        .blockHeartbeat(request).getCommand(), LOG, "Heartbeat", "workerId=%d", workerId);
  }

  /**
   * The method the worker should execute to register with the block master.
   *
   * @param workerId the worker id of the worker registering
   * @param storageTierAliases a list of storage tier aliases in ordinal order
   * @param totalBytesOnTiers mapping from storage tier alias to total bytes
   * @param usedBytesOnTiers mapping from storage tier alias to used bytes
   * @param currentBlocksOnLocation mapping from storage tier alias to the list of list of blocks
   * @param lostStorage mapping from storage tier alias to the list of lost storage paths
   * @param configList a list of configurations
   */
  // TODO(yupeng): rename to workerBlockReport or workerInitialize?
  public void register(final long workerId, final List<String> storageTierAliases,
      final Map<String, Long> totalBytesOnTiers, final Map<String, Long> usedBytesOnTiers,
      final Map<BlockStoreLocation, List<Long>> currentBlocksOnLocation,
      final Map<String, List<String>> lostStorage,
      final List<ConfigProperty> configList) throws IOException {

    final RegisterWorkerPOptions options =
        RegisterWorkerPOptions.newBuilder().addAllConfigs(configList).build();

    // TODO(jiacheng): this part of logic needs to update
    final List<LocationBlockIdListEntry> currentBlocks
        = convertBlockListMapToProto(currentBlocksOnLocation);

    final Map<String, StorageList> lostStorageMap = lostStorage.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey,
            e -> StorageList.newBuilder().addAllStorage(e.getValue()).build()));

    // TODO(jiacheng): break this into chunks?
    // 1st request, all but blocks
    final RegisterWorkerPRequest request = RegisterWorkerPRequest.newBuilder().setWorkerId(workerId)
        .addAllStorageTiers(storageTierAliases).putAllTotalBytesOnTiers(totalBytesOnTiers)
        .putAllUsedBytesOnTiers(usedBytesOnTiers)
//        .addAllCurrentBlocks(currentBlocks)
        .putAllLostStorage(lostStorageMap)
        .setOptions(options).build();

    // This is what the code used to be
//    retryRPC(() -> {
//                    mClient.registerWorker(request);
//            }

    final CountDownLatch finishLatch = new CountDownLatch(1);
    retryRPC(() -> {
      // TODO(jiacheng): change to streaming
      StreamObserver<RegisterWorkerPResponse> responseObserver = new StreamObserver<RegisterWorkerPResponse>() {
        @Override
        public void onNext(RegisterWorkerPResponse response) {
          // The response is empty
          LOG.info("Received worker registration response, waiting to complete.");
          // If the complete never comes, do we set a timeout and assume to complete?
        }

        @Override
        public void onError(Throwable t) {
          // TODO(jiacheng): how do i propagate this to the retryRPC?
          throw t;
        }

        @Override
        public void onCompleted() {
          LOG.info("Worker registration received success message from master.");
          finishLatch.countDown();
        }
      };

      StreamObserver<RegisterWorkerPRequest> requestObserver = mStreamClient.registerWorker(responseObserver);

      try {
        // Build a series of RegisterWorkerPRequests and stream to the master
        // The 1st request is all the metadata except blocks
        // The lost storage field is the StorageDir paths that fail to initialize
        // or errs out on storage checks
        // The size should be small despite being a list, so it can go with the 1st request
        // On the following requests, send the block locations in chunks

      } catch (Throwable e) {
        // TODO(jiacheng): Cancel RPC
        requestObserver.onError(e);
        throw e;
      }
      // Mark the end of requests
      requestObserver.onCompleted();

      // Receiving happens asynchronously
      try {
        // Set timeout
        // TODO(jiacheng): make configurable
        if (finishLatch.await(10, TimeUnit.MINUTES)) {
          // If the return is true, then the countdown has finished
          LOG.info("Finished register");
          return null;
        } else {
          // If the return is false, then the countdown has timed out
          // TODO(jiacheng)
          LOG.error("Failed to finish register in 10 minutes!");
          throw new RuntimeException("Failed to finish register in 10 min.");
        }
      } catch (InterruptedException e) {
        // TODO(jiacheng)
      }
    }, LOG, "Register", "workerId=%d", workerId);
  }
}

package alluxio.cli.log;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LogParserTest {
  @Test
  public void parseLogFile() throws Exception {
    String path = "/Users/jiachengliu/Documents/Alluxio/test/master.log.small";
    List<LogEntry> entries = LogParser.parseFile(path, entry -> true);
    System.out.println(entries);

    entries.stream().mapToInt(LogEntry::getLineCount).forEach(System.out::println);
    int totalLines = entries.stream().mapToInt(LogEntry::getLineCount).sum();

    System.out.println("Check 1st line");
    System.out.println(entries.get(0));
    System.out.println("========");
    System.out.println(entries.get(1));
    System.out.println("========");

    assertEquals(countLines(path), totalLines);
  }

  @Test
  public void parseLogFileInfo() throws Exception {
    String path = "/Users/jiachengliu/Documents/Alluxio/test/master.log.small";
    List<LogEntry> entries = LogParser.parseFile(path, entry -> entry.mLevel.infoOrAbove());
    System.out.println(entries);

    entries.stream().mapToInt(LogEntry::getLineCount).forEach(System.out::println);

    assertEquals(0, entries.size());
  }

  @Test
  public void parseJobLogFile() throws Exception {
    String path = "/Users/jiachengliu/Documents/Alluxio/test/job_master.log.small";
    List<LogEntry> entries = LogParser.parseFile(path, entry -> true);
    System.out.println(entries);

    entries.stream().mapToInt(LogEntry::getLineCount).forEach(System.out::println);
    int totalLines = entries.stream().mapToInt(LogEntry::getLineCount).sum();

    System.out.println("Check 1st line");
    System.out.println(entries.get(0));
    System.out.println("========");
    System.out.println(entries.get(1));
    System.out.println("========");

    assertEquals(countLines(path), totalLines);
  }

  @Test
  public void parseJobLogFileErrors() throws Exception {
    String path = "/Users/jiachengliu/Documents/Alluxio/test/job_master.log.small";
    List<LogEntry> entries = LogParser.parseFile(path, entry -> entry.mLevel.errorOrAbove());
    System.out.println(entries);

    entries.stream().mapToInt(LogEntry::getLineCount).forEach(System.out::println);

    assertEquals(6, entries.size());
  }

  private int countLines(String path) throws IOException {
      // Read file with a BufferedReader
    FileReader fr = new FileReader(path);
    BufferedReader br = new BufferedReader(fr);

    int lineCount = 0;
    while (br.readLine() != null) {
      lineCount++;
    }
    return lineCount;
  }

  @Test
  public void parseString() throws Exception {
    String rows = "2019-12-14 04:04:01,044 ERROR ProcessUtils - Uncaught exception while running Alluxio worker @{}, stopping it and exiting. Exception \"alluxio.exception.status.UnavailableException: Failed after {} attempts: alluxio.exception.status.CancelledException: HTTP/2 error code: CANCEL\n" +
            "Received Rst Stream\", Root Cause \"io.grpc.StatusRuntimeException: CANCELLED: HTTP/2 error code: CANCEL\n" +
            "Received Rst Stream\"\n" +
            "alluxio.exception.status.UnavailableException: Failed after 44 attempts: alluxio.exception.status.CancelledException: HTTP/2 error code: CANCEL\n" +
            "Received Rst Stream\n" +
            "        at alluxio.AbstractClient.retryRPCInternal(AbstractClient.java:399)\n" +
            "        at alluxio.AbstractClient.retryRPC(AbstractClient.java:344)\n" +
            "        at alluxio.worker.block.BlockMasterClient.register(BlockMasterClient.java:222)\n" +
            "        at alluxio.worker.block.BlockMasterSync.registerWithMaster(BlockMasterSync.java:106)\n" +
            "        at alluxio.worker.block.BlockMasterSync.<init>(BlockMasterSync.java:93)\n" +
            "        at alluxio.worker.block.DefaultBlockWorker.start(DefaultBlockWorker.java:214)\n" +
            "        at alluxio.worker.block.DefaultBlockWorker.start(DefaultBlockWorker.java:77)\n" +
            "        at alluxio.Registry.start(Registry.java:131)\n" +
            "        at alluxio.worker.AlluxioWorkerProcess.startWorkers(AlluxioWorkerProcess.java:283)\n" +
            "        at alluxio.worker.AlluxioWorkerProcess.start(AlluxioWorkerProcess.java:235)\n" +
            "        at alluxio.ProcessUtils.run(ProcessUtils.java:35)\n" +
            "        at alluxio.worker.AlluxioWorker.main(AlluxioWorker.java:71)\n" +
            "Caused by: alluxio.exception.status.CancelledException: HTTP/2 error code: CANCEL\n" +
            "Received Rst Stream\n" +
            "        at alluxio.exception.status.AlluxioStatusException.from(AlluxioStatusException.java:125)\n" +
            "        at alluxio.exception.status.AlluxioStatusException.fromStatusRuntimeException(AlluxioStatusException.java:210)\n" +
            "        at alluxio.AbstractClient.retryRPCInternal(AbstractClient.java:384)\n" +
            "        ... 11 more\n" +
            "Caused by: io.grpc.StatusRuntimeException: CANCELLED: HTTP/2 error code: CANCEL\n" +
            "Received Rst Stream\n" +
            "        at io.grpc.stub.ClientCalls.toStatusRuntimeException(ClientCalls.java:233)\n" +
            "        at io.grpc.stub.ClientCalls.getUnchecked(ClientCalls.java:214)\n" +
            "        at io.grpc.stub.ClientCalls.blockingUnaryCall(ClientCalls.java:139)\n" +
            "        at alluxio.grpc.BlockMasterWorkerServiceGrpc$BlockMasterWorkerServiceBlockingStub.registerWorker(BlockMasterWorkerServiceGrpc.java:477)\n" +
            "        at alluxio.worker.block.BlockMasterClient.lambda$register$6(BlockMasterClient.java:223)\n" +
            "        at alluxio.AbstractClient.retryRPCInternal(AbstractClient.java:382)\n" +
            "        ... 11 more\n" +
            "2019-12-14 04:04:01,062 INFO  GrpcDataServer - Shutting down Alluxio worker gRPC server at 0.0.0.0/0.0.0.0:29999.\"";


    Reader inputString = new StringReader(rows);
    BufferedReader reader = new BufferedReader(inputString);

    List<LogEntry> entries = LogParser.parsePattern(reader, LogParser.LOG_PATTERN, e -> true);
    assertEquals(2, entries.size());
  }

  @Test
  public void parseLines() throws Exception {
    // Example 1:
    // 2020-08-19 20:34:03,864 ERROR UfsJournalCheckpointThread - FileSystemMaster: Failed to create checkpoint
    //com.esotericsoftware.kryo.KryoException: org.apache.hadoop.ipc.RemoteException(org.apache.hadoop.hdfs.server.namenode.LeaseExpiredException): No lease on /journal2_2_1_1_2_d/FileSystemMaster/v1/.tmp/85e32400-4083-463a-a549-8b8d0cbc4c25 (inode 39243154): File does not exist. Holder DFSClient_NONMAPREDUCE_1577667095_84 does not have any open files.
    // at ..
    // Caused by: org.apache.hadoop.ipc.RemoteException(org.apache.hadoop.hdfs.server.namenode.LeaseExpiredException): No lease on /journal2_2_1_1_2_d/FileSystemMaster/v1/.tmp/85e32400-4083-463a-a549-8b8d0cbc4c25 (inode 39243154): File does not exist. Holder DFSClient_NONMAPREDUCE_1577667095_84 does not have any open files.
    // at ..

    // Example 2:
    String ex2 = "2019-12-14 04:04:01,044 ERROR ProcessUtils - Uncaught exception while running Alluxio worker @{}, stopping it and exiting. Exception \"alluxio.exception.status.UnavailableException: Failed after {} attempts: alluxio.exception.status.CancelledException: HTTP/2 error code: CANCEL\n" +
            "Received Rst Stream\", Root Cause \"io.grpc.StatusRuntimeException: CANCELLED: HTTP/2 error code: CANCEL\n" +
            "Received Rst Stream\"\n" +
            "alluxio.exception.status.UnavailableException: Failed after 44 attempts: alluxio.exception.status.CancelledException: HTTP/2 error code: CANCEL\n" +
            "Received Rst Stream\n" +
            "        at alluxio.AbstractClient.retryRPCInternal(AbstractClient.java:399)\n" +
            "        at alluxio.AbstractClient.retryRPC(AbstractClient.java:344)\n" +
            "        at alluxio.worker.block.BlockMasterClient.register(BlockMasterClient.java:222)\n" +
            "        at alluxio.worker.block.BlockMasterSync.registerWithMaster(BlockMasterSync.java:106)\n" +
            "        at alluxio.worker.block.BlockMasterSync.<init>(BlockMasterSync.java:93)\n" +
            "        at alluxio.worker.block.DefaultBlockWorker.start(DefaultBlockWorker.java:214)\n" +
            "        at alluxio.worker.block.DefaultBlockWorker.start(DefaultBlockWorker.java:77)\n" +
            "        at alluxio.Registry.start(Registry.java:131)\n" +
            "        at alluxio.worker.AlluxioWorkerProcess.startWorkers(AlluxioWorkerProcess.java:283)\n" +
            "        at alluxio.worker.AlluxioWorkerProcess.start(AlluxioWorkerProcess.java:235)\n" +
            "        at alluxio.ProcessUtils.run(ProcessUtils.java:35)\n" +
            "        at alluxio.worker.AlluxioWorker.main(AlluxioWorker.java:71)\n" +
            "Caused by: alluxio.exception.status.CancelledException: HTTP/2 error code: CANCEL\n" +
            "Received Rst Stream\n" +
            "        at alluxio.exception.status.AlluxioStatusException.from(AlluxioStatusException.java:125)\n" +
            "        at alluxio.exception.status.AlluxioStatusException.fromStatusRuntimeException(AlluxioStatusException.java:210)\n" +
            "        at alluxio.AbstractClient.retryRPCInternal(AbstractClient.java:384)\n" +
            "        ... 11 more\n" +
            "Caused by: io.grpc.StatusRuntimeException: CANCELLED: HTTP/2 error code: CANCEL\n" +
            "Received Rst Stream\n" +
            "        at io.grpc.stub.ClientCalls.toStatusRuntimeException(ClientCalls.java:233)\n" +
            "        at io.grpc.stub.ClientCalls.getUnchecked(ClientCalls.java:214)\n" +
            "        at io.grpc.stub.ClientCalls.blockingUnaryCall(ClientCalls.java:139)\n" +
            "        at alluxio.grpc.BlockMasterWorkerServiceGrpc$BlockMasterWorkerServiceBlockingStub.registerWorker(BlockMasterWorkerServiceGrpc.java:477)\n" +
            "        at alluxio.worker.block.BlockMasterClient.lambda$register$6(BlockMasterClient.java:223)\n" +
            "        at alluxio.AbstractClient.retryRPCInternal(AbstractClient.java:382)\n" +
            "        ... 11 more\n";

    String ex3 = "java.lang.RuntimeException: alluxio.exception.FileDoesNotExistException: inodeId {} does not exist; too many retries\n" +
            "        at alluxio.master.file.DefaultFileSystemMaster.completeFileFromEntry(DefaultFileSystemMaster.java:1294)\n" +
            "        at alluxio.master.file.DefaultFileSystemMaster.processJournalEntry(DefaultFileSystemMaster.java:478)\n" +
            "        at alluxio.master.journal.ufs.UfsJournalCheckpointThread.runInternal(UfsJournalCheckpointThread.java:146)\n" +
            "        at alluxio.master.journal.ufs.UfsJournalCheckpointThread.run(UfsJournalCheckpointThread.java:123)\n" +
            "Caused by: alluxio.exception.FileDoesNotExistException: inodeId 10113188364287 does not exist; too many retries\n" +
            "        at alluxio.master.file.meta.InodeTree.lockFullInodePath(InodeTree.java:393)\n" +
            "        at alluxio.master.file.DefaultFileSystemMaster.completeFileFromEntry(DefaultFileSystemMaster.java:1290)\n" +
            "        ... 3 more";

    String ex4 = "Exception in thread \"main\" java.lang.IllegalArgumentException: The URI authority {} does not match the configured value of {}\n" +
            "at alluxio.client.file.BaseFileSystem.checkUri(BaseFileSystem.java:583)\n" +
            "at alluxio.client.file.BaseFileSystem.openFile(BaseFileSystem.java:410)\n" +
            "at alluxio.client.file.BaseFileSystem.openFile(BaseFileSystem.java:404)\n" +
            "at com.huawei.alluxio.Main.main(Main.java:27)";

    String ex5 = "2019-12-02 18:24:45,472 WARN  FileSystemMasterClientServiceHandler - Exit (Error): Free: request=path: {}\n" +
            "options {\n" +
            "  recursive: true\n" +
            "  forced: false\n" +
            "  commonOptions {\n" +
            "    syncIntervalMs: -1\n" +
            "    ttl: -1\n" +
            "    ttlAction: DELETE\n" +
            "  }\n" +
            "}\n" +
            ", Error=alluxio.exception.UnexpectedAlluxioException: Cannot free file {} which is not persisted\n" +
            "2019-12-02 18:25:08,774 WARN  DefaultFileSystemMaster - Failed to persist file {}, will retry later: alluxio.exception.status.Unavailabl\n" +
            "eException: Failed to determine address for JobMasterClient after {} attempts\n";


  }

}

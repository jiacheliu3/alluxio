package alluxio.cli;

import alluxio.AlluxioURI;
import alluxio.client.file.FileOutStream;
import alluxio.client.file.FileSystem;
import alluxio.grpc.CreateFilePOptions;
import alluxio.grpc.FileSystemMasterCommonPOptions;
import alluxio.grpc.WritePType;
import org.junit.Test;

public class CommitTest {
  @Test
  public void testCommit() throws Exception {
    String path = "/test_file_large8";
    FileSystem fs = FileSystem.Factory.get();
    AlluxioURI a_path = new AlluxioURI(path);

    byte[] content = "0123456789".getBytes(); //10byte
    FileSystemMasterCommonPOptions commonPOptions = FileSystemMasterCommonPOptions.newBuilder().setSyncIntervalMs(-1).build();
    CreateFilePOptions options = CreateFilePOptions.newBuilder().setWriteType(WritePType.CACHE_THROUGH).setCommonOptions(commonPOptions).build();
    FileOutStream out = fs.createFile(a_path, options);

    int max = 65 * 1024 * 1024/content.length;
    long counter = 0;
    for(int i=0; i < max; i++) {
      out.write(content);
      counter += content.length;
      if(counter >= 1 * 1024 * 1024)
        try {
          Thread.sleep(500L);
          System.out.println("Iter " + i + ": " + fs.getStatus(a_path)); //触发问题
          counter = 0;
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
    }
    out.flush();
    out.close();
    System.out.println(fs.getStatus(a_path));
  }

}

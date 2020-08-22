package alluxio.cli.log;

import org.junit.Test;

public class LogAnalyzerTest {
  @Test
  public void walkATarball() throws Exception {
    String testTarball = "/Users/jiachengliu/Documents/Alluxio/test/collect/alluxio-cluster-info-20200818_100450.tar.gz";
    LogAnalyzer.parseLogsInTarball(testTarball);
  }
}

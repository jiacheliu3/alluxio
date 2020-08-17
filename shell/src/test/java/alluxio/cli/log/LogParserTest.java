package alluxio.cli.log;

import org.junit.Test;

import java.util.List;

public class LogParserTest {
  @Test
  public void parseSmallFile() throws Exception {
    String path = "/Users/jiachengliu/Documents/Alluxio/test/master.log.small";
    List<LogParser.LogEntry> entries = LogParser.parseFile(path);
    System.out.println(entries);
  }

}

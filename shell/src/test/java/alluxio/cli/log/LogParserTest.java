package alluxio.cli.log;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LogParserTest {
  @Test
  public void parseLogFile() throws Exception {
    String path = "/Users/jiachengliu/Documents/Alluxio/test/master.log.small";
    List<LogParser.LogEntry> entries = LogParser.parseFile(path, entry -> true);
    System.out.println(entries);

    entries.stream().mapToInt(LogParser.LogEntry::getLineCount).forEach(System.out::println);
    int totalLines = entries.stream().mapToInt(LogParser.LogEntry::getLineCount).sum();

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
    List<LogParser.LogEntry> entries = LogParser.parseFile(path, entry -> entry.mLevel.infoOrAbove());
    System.out.println(entries);

    entries.stream().mapToInt(LogParser.LogEntry::getLineCount).forEach(System.out::println);

    assertEquals(0, entries.size());
  }

  @Test
  public void parseJobLogFile() throws Exception {
    String path = "/Users/jiachengliu/Documents/Alluxio/test/job_master.log.small";
    List<LogParser.LogEntry> entries = LogParser.parseFile(path, entry -> true);
    System.out.println(entries);

    entries.stream().mapToInt(LogParser.LogEntry::getLineCount).forEach(System.out::println);
    int totalLines = entries.stream().mapToInt(LogParser.LogEntry::getLineCount).sum();

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
    List<LogParser.LogEntry> entries = LogParser.parseFile(path, entry -> entry.mLevel.errorOrAbove());
    System.out.println(entries);

    entries.stream().mapToInt(LogParser.LogEntry::getLineCount).forEach(System.out::println);

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

}

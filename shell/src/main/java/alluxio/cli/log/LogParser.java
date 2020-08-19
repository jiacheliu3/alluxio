package alluxio.cli.log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogParser {
  // Prepare patterns
  public static Pattern JOB_LOG_PATTERN = Pattern.compile("(?<datetime>\\S+\\S+\\S+\\S+-\\S+\\S+-\\S+\\S+ \\S+\\S+:\\S+\\S+:\\S+\\S+,\\S+\\S+\\S+)[ ]+(?<level>\\s*?\\S*?\\s*?)[ ]+(?<className>\\s*?\\S*?\\s*?)[ ]+\\((?<classFile>.*?):(?<method>.*?)\\)[ ]+\\-[ ]+(?<message>(.*))");
  public static Pattern LOG_PATTERN = Pattern.compile("(?<datetime>\\S+\\S+\\S+\\S+-\\S+\\S+-\\S+\\S+ \\S+\\S+:\\S+\\S+:\\S+\\S+,\\S+\\S+\\S+)[ ]+(?<level>\\s*?\\S*?\\s*?)[ ]+(?<className>\\s*?\\S*?\\s*?)[ ]+\\-[ ]+(?<message>(.*))");
  public static Pattern EXCEPTION_PATTERN = Pattern.compile("^\\s+at.*");

  public static List<LogEntry> parseFile(String path, Predicate<LogEntry> shouldKeep) throws IOException {
    // TODO: close the readers
    FileReader fr;
    BufferedReader br;
    try {
      // Read file with a BufferedReader
      fr = new FileReader(path);
      br = new BufferedReader(fr);
    } catch (FileNotFoundException e) {
      // TODO
      return null;
    }

    // Decide which pattern to match
    File f = new File(path);
    String name = f.getName();
    Pattern logPattern;
    // TODO(jiacheng): parse .out files
    if (name.startsWith("job_")) {
      System.out.println("job master/worker log");
      logPattern = JOB_LOG_PATTERN;
    } else {
      System.out.println("master/worker log");
      logPattern = LOG_PATTERN;
    }

    Matcher eventMatcher;
    Matcher exceptionMatcher;
    String line;
    LogEntry current = null;
    List<LogEntry> entries = new ArrayList<>();

    while ((line = br.readLine()) != null) {
      // Skip empty lines
      if (line.trim().equals("")) {
        continue;
      }

      // Case 1: new entry
      eventMatcher = logPattern.matcher(line);
      if (eventMatcher.matches()) {
        // TODO
        // Job master/worker logs have classFile:method
        // Do we need these fields?

        // TODO: exceptions getting the groups
        current = new LogEntry(eventMatcher.group("datetime"),
                eventMatcher.group("level"),
                eventMatcher.group("className"),
                eventMatcher.group("message"));
        if (shouldKeep.test(current)) {
          entries.add(current);
        }

        System.out.format("New entry found %s%n", current.toString());
        continue;
      }

      // Case 2: entry cont. stacktrace
      exceptionMatcher = EXCEPTION_PATTERN.matcher(line);
      if (exceptionMatcher.matches()) {
        current.appendMessage(line);
        continue;
      }

      // Case 3: entry cont., not stacktrace
      current.appendMessage(line);
      continue;
    }

    return entries;
  }

  public static class LogEntry {
    // 2020-07-23 04:36:05,485
    static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS");

    final LocalDateTime mDateTime;
    final LogLevel mLevel;
    final String mClassName;
    final StringBuffer mMessage;
    private AtomicInteger mLineCount;

    public LogEntry(String dt, String l, String c, String m) {
      // Parse datetime
      mDateTime = LocalDateTime.parse(dt, formatter);
      mLevel = LogLevel.findLogLevel(l);
      mClassName = c;
      mMessage = new StringBuffer(m);
      mLineCount = new AtomicInteger(1);
    }

    public void appendMessage(String line) {
      mMessage.append("\n");
      mMessage.append(line);
      mLineCount.incrementAndGet();
    }

    public String getMessage() {
      return mMessage.toString();
    }

    public int getLineCount() {
      return mLineCount.get();
    }

    @Override
    public String toString() {
      // 2020-04-23 10:26:22,033 DEBUG CopycatGrpcConnection -
      // Connection failed: CopycatGrpcClientConnection{ConnectionOwner=CLIENT, ConnectionId=256425,
      return String.format("%s %s %s - %s", mDateTime, mLevel, mClassName, mMessage.toString());
    }
  }

  enum LogLevel {
    TRACE,
    DEBUG,
    INFO,
    ERROR,
    WARN,
    FATAL;

    public static LogLevel findLogLevel(String level) {
      switch (level) {
        case "TRACE":
          return TRACE;
        case "DEBUG":
          return DEBUG;
        case "INFO":
          return INFO;
        case "ERROR":
          return ERROR;
        case "WARN":
          return WARN;
        case "FATAL":
          return FATAL;
        default:
          throw new IllegalArgumentException(String.format("Unknown level %s", level));
      }
    }

    public boolean infoOrAbove() {
      return this == INFO || warningOrAbove();
    }

    public boolean warningOrAbove() {
      return this == WARN || errorOrAbove();
    }

    public boolean errorOrAbove() {
      return this == ERROR || this == FATAL;
    }
  }
}


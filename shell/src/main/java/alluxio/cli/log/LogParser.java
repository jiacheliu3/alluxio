package alluxio.cli.log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogParser {
  // Prepare patterns
  public static Pattern LOG_PATTERN = Pattern.compile("(?<datetime>\\S+\\S+\\S+\\S+-\\S+\\S+-\\S+\\S+ \\S+\\S+:\\S+\\S+:\\S+\\S+,\\S+\\S+\\S+)[ ]+(?<level>\\s*?\\S*?\\s*?)[ ]+(?<className>\\s*?\\S*?\\s*?)[ ]+(\\((?<classFile>.*?):(?<method>.*?)\\)[ ]+)?\\-[ ]+(?<message>(.*))");
//  public static Pattern LOG_PATTERN = Pattern.compile("(?<datetime>\\S+\\S+\\S+\\S+-\\S+\\S+-\\S+\\S+ \\S+\\S+:\\S+\\S+:\\S+\\S+,\\S+\\S+\\S+)[ ]+(?<level>\\s*?\\S*?\\s*?)[ ]+(?<className>\\s*?\\S*?\\s*?)[ ]+\\-[ ]+(?<message>(.*))");
  public static Pattern EXCEPTION_PATTERN = Pattern.compile("^\\s+at.*");

  public static List<LogEntry> parseFile(String path, Predicate<LogEntry> shouldKeep) throws IOException {
    try(
    FileReader  fr = new FileReader(path);
    BufferedReader  br = new BufferedReader(fr)
    ) {
      // Decide which pattern to match
      File f = new File(path);
      Pattern logPattern = LOG_PATTERN;
      // TODO(jiacheng): parse .out files

      List<LogEntry> entries = parsePattern(br, logPattern, shouldKeep);
      System.out.format("File %s: %s entries kept%n", path, entries.size());

      return entries;
    }
  }

  /**
   * This parses input from a log
   * The characteristic is, the input are always formatted
   *
   * */
  public static List<LogEntry> parsePattern(BufferedReader reader, Pattern logPattern, Predicate<LogEntry> shouldKeep) throws IOException {
    Matcher eventMatcher;
    Matcher exceptionMatcher;
    String line;
    LogEntry current = null;
    List<LogEntry> entries = new ArrayList<>();

    while ((line = reader.readLine()) != null) {
      // Skip empty lines
      if (line.trim().equals("")) {
        continue;
      }

      // Case 1: new entry
      eventMatcher = logPattern.matcher(line);
      if (eventMatcher.matches()) {
        // TODO: exceptions getting the groups
        current = new LogEntry(eventMatcher.group("datetime"),
                eventMatcher.group("level"),
                eventMatcher.group("className"),
                eventMatcher.group("message"));

        // TODO: change this to a callback
        // TODO: replace the real message with the template?
        // TODO: keep only the top 5 traces?
        if (shouldKeep.test(current)) {
          entries.add(current);
        }
        continue;
      }

      // Case 2: entry cont. stacktrace
      exceptionMatcher = EXCEPTION_PATTERN.matcher(line);
      if (exceptionMatcher.matches()) {
        if (current == null) {
          System.err.format("Found chunked log line %s. Missing first line of entry!%n");
          continue;
        }
        current.appendMessage(line);
        current.setIsError();
        continue;
      }

      // Case 3: entry cont., not stacktrace
      if (current == null) {
        System.err.format("Found chunked log line %s. Missing first line of entry!%n");
        continue;
      }
      current.appendMessage(line);
      continue;
    }

    return entries;
  }

  // Pass in the message field of the LogEntry
  public static Pattern extractPattern(String lines) {
    int index = lines.indexOf("\n");
    String firstLine = lines;
    // Only look at the 1st row
    if (index != -1) {
      firstLine = lines.substring(0, index);
    }

    // TODO: Do we need to make sure of this somewhere?
//    // Extract the message
//    Pattern pattern = LOG_PATTERN;
//    Matcher m = pattern.matcher(lines);
//    String message;
//    if (m.matches()) {
//      message = m.group("message");
//    } else {
//      message = lines;
//    }

    // The real message is the last piece of message after :
    // TODO: Is this really the way? Think about this heuristic again
    int lastColon = firstLine.lastIndexOf(':');
    if (lastColon != -1 && lastColon != message.length()) {
      message = message.substring(lastColon + 1);
    }

    // Find the 1st exception
    // <1st exception>:

    // Convert the template into regex pattern
    String p = message.replaceAll("\\{.*\\}", ".*");
    System.out.format("Extracted template %s%n", p);
    return Pattern.compile(p);
  }

  public void extractExceptions(String lines) throws IOException {
    //
  }
}


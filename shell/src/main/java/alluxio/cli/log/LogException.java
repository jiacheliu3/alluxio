package alluxio.cli.log;

/**
 * A LogException is an exception in the log
 * One LogEntry can have multiple exceptions that are nested
 * Typically
 */

import java.util.regex.Pattern;
public class LogException {
  TemplatedMessage mMessage;
  StackTraceElement[] mTopOfStack;

  public LogException(LogEntry entry) {

  }


  public static class TemplatedMessage {
    String mMessage;
    Pattern mPattern;
  }
}

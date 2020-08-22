package alluxio.cli.log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public class LogEntry {
  // 2020-07-23 04:36:05,485
  static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS");

  final LocalDateTime mDateTime;
  final LogLevel mLevel;
  final String mClassName;
  final StringBuffer mMessage;
  private AtomicInteger mLineCount;

  private boolean mIsError;

  public LogEntry(String datetime, String level, String className, String message) {
    // Parse datetime
    mDateTime = LocalDateTime.parse(datetime, formatter);
    mLevel = LogLevel.findLogLevel(level);
    mClassName = className;
    mMessage = new StringBuffer(message);
    mLineCount = new AtomicInteger(1);

    // TODO: configure if we need warning in the command
    if (mLevel.warningOrAbove()) {
      mIsError = true;
    }
  }

  public void setIsError() {
    mIsError = true;
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
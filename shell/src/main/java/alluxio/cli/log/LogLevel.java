package alluxio.cli.log;

public enum LogLevel {
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

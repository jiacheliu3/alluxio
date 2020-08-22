package alluxio.cli.log;

import java.util.regex.Pattern;

public class LogTemplate {
  // Where this log is from
//  Component mComponent;

//  String mClassName;
  Pattern mPattern;

//  public LogTemplate(String className, Pattern pattern, Component component) {
//    mClassName = className;
//    mPattern = pattern;
//    mComponent = component;
//  }

  public LogTemplate(Pattern pattern) {
    mPattern = pattern;
  }

  public static enum Component {
    MASTER,
    WORKER,
    CLIENT,
    OTHERS
  }
}


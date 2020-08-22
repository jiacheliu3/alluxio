package alluxio.cli.log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IssueEntry {
  // Store all templates here
  // TODO: maybe move to another class?
  private static final Map<LogTemplate.Component, Queue<LogTemplate>> TEMPLATES = new ConcurrentHashMap<>();
  static {
    TEMPLATES.put(LogTemplate.Component.MASTER, new ConcurrentLinkedQueue<>());
    TEMPLATES.put(LogTemplate.Component.WORKER, new ConcurrentLinkedQueue<>());
    TEMPLATES.put(LogTemplate.Component.CLIENT, new ConcurrentLinkedQueue<>());
    TEMPLATES.put(LogTemplate.Component.OTHERS, new ConcurrentLinkedQueue<>());
  }


  String mID;
  String mName;
  // TODO: convert to LogEntry?
  // TODO: job master/worker
  String mClientLog;
  String mMasterLog;
  String mWorkerLog;
  String mOthersLog;

  String mSymptom;
  String mLink;
  String mSolution;
  String mExplanation;
  String mWorkaround;

  String mTags;
  String mChangeID;
  String mFoundVersion;
  String mFixedVersion;

  // TODO: log template?
  String mLogTemplate;

  static Pattern componentPattern = Pattern.compile("\\[?<component>\\]");

  public void processTemplates() {
    processTemplate(mClientLog, LogTemplate.Component.CLIENT);
    processTemplate(mWorkerLog, LogTemplate.Component.WORKER);
    processTemplate(mMasterLog, LogTemplate.Component.MASTER);
    processTemplate(mOthersLog, LogTemplate.Component.OTHERS);
  }

  // TODO: Handle one cell multiple LogEntries
  public void processTemplate(String s, LogTemplate.Component c) throws IOException  {
    // Do nothing if there's no such component
    if (s == null) {
      return;
    }

    // TODO: try with resource
    BufferedReader br = new BufferedReader(new StringReader(s));
    String line;
    LogEntry current = null;
    while ((line = br.readLine()) != null) {
      // Case 1: the line means which component(log file) this log is from
      Matcher componentMatcher = componentPattern.matcher(line);
      if (componentMatcher.matches()) {
        // This is a component name
        String componentName = componentMatcher.group("component");
        System.out.format("The following log entrys belong to component %s%n", componentName);

        // TODO: how do we serve this component name information to the user?
        // Now we don't do anything but treating this log entry the same way

        // Cut the logs here for a new entry
        current = null;
        continue;
      }

      // Case 2: Exception line with format
      // This means the start of a LogEntry
      Matcher logMatcher = LogParser.LOG_PATTERN.matcher(line);
      if (logMatcher.matches()) {
        // TODO: Maybe this does not have datetime & level?
        current = new LogEntry(logMatcher.group("datetime"),
                logMatcher.group("level"),
                logMatcher.group("className"),
                logMatcher.group("message"));

        // TODO: change this to a callback
        // TODO: replace the real message with the template?
        // TODO: keep only the top 5 traces?

        if (shouldKeep.test(current)) {
          // TODO: process this current LogEntry

          // One LogEntry has
          // One message, multiple exceptions
          current.process();
        }

        continue;
      }

      // Case 3: Append to the existing LogEntry
      if (current == null) {
        // TODO:
      }
      current.appendMessage(line);
      continue;
    }

    // For each line, if it is exception,



    // TODO: Return the LogEntry?
    Pattern pattern = LogParser.extractPattern(s);

    // create the template obj
    LogTemplate template = new LogTemplate(pattern);

    // TODO: add to corresponding data structures
    TEMPLATES.get(c).add(template);
  }



  public List<LogEntry> processCell(String s) {
    // Convert rows in this cell into a set of LogEntry objects
    // The difference is, the LogEntry can be missing fields

  }

}

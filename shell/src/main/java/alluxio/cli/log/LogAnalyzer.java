package alluxio.cli.log;

import alluxio.cli.bundler.TarUtils;
import alluxio.collections.PrefixList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LogAnalyzer {
  public static final Set<String> FILE_NAMES_PREFIXES = Stream.of(
          "master.log",
          "master.out",
          "job_master.log",
          "job_master.out",
          "master_audit.log",
          "worker.log",
          "worker.out",
          "job_worker.log",
          "job_worker.out",
          "proxy.log",
          "proxy.out",
          // TODO: this one has different format
//          "task.log",
          "task.out",
          "user_"
  ).collect(Collectors.toSet());




  // TODO: the command interface, do i put it here?

  static Set<String> shouldKeepClasses = ImmutableSet.of("AbstractPrimarySelector");

  // <Node, List<LogEntry>>
  static Map<String, Queue<LogEntry>> errors = new ConcurrentHashMap<>();
  static Map<String, Queue<LogEntry>> warnings = new ConcurrentHashMap<>();
  static Map<String, Queue<LogEntry>> fatals = new ConcurrentHashMap<>();

  static PriorityQueue<Pair<String, LogEntry>> primacy = new PriorityQueue<>(Comparator.comparing(a -> a.getRight().mDateTime)
  );




  // Directory walker
  public static void parseLogsInTarball(String path) throws IOException {
    // Create temp dir for unzip
    File tempDir = createTemporaryDirectory();

    // Unzip tarball
    TarUtils.decompress(path, tempDir);

    // Walk the output dir
    walk(tempDir);

    // Get nodes
    List<String> nodes = new ArrayList<>();
    Map<String, File> logDirs = new HashMap<>();
    for (File f : tempDir.listFiles()) {
      String hostname = f.getName();
      System.out.format("Node name %s%n", hostname);
      nodes.add(hostname);

      // Unzip the local tarball
      File localTarball = new File(f, "alluxio-info.tar.gz");
      File localTempDir = createTemporaryDirectory();
      TarUtils.decompress(localTarball.getCanonicalPath(), localTempDir);

      File logDir = new File(localTempDir, "collectLog");
      if (!logDir.exists()) {
        System.err.format("Log dir at %s is not found!%n", logDir.getCanonicalPath());
        return;
      }
      logDirs.put(hostname, logDir);
      System.out.format("Log dir for host %s at %s%n", hostname, logDir.getCanonicalPath());

      // Print sth to verify
      System.out.format("%s%n", Arrays.toString(logDir.list()));
    }

    // TODO: multi-thread this part

    // Process log dirs one by one
    System.out.format("Found logs for nodes %s%n", nodes);
    System.out.format("Log dirs %s%n", logDirs);
    for (Map.Entry<String, File> entry : logDirs.entrySet()) {
      String hostname = entry.getKey();
      File logDir = entry.getValue();
      System.out.format("Processing logdir %s%n", logDir.getCanonicalPath());

      // Parse each log file in the log directory
      for (File f : logDir.listFiles()) {
        System.out.format("Processing file %s%n", f.getCanonicalPath());

        // Check if we want to parse the file
        if (!isAlluxioLog(f)) {
          continue;
        }

        // Parse one file
        List<LogEntry> entries = LogParser.parseFile(f.getCanonicalPath(), e -> {
          // Keep WARN/ERROR/FATAL entries and those in the whitelist
          if (e.mLevel.warningOrAbove()) {
            return true;
          } else {
            if (shouldKeepClasses.contains(e.mClassName)) {
              return true;
            }
            return false;
          }
        });

        // TODO: process the entries
        System.out.format("%s entries found parsing file%n", entries.size(), path);

        for (LogEntry log : entries) {
          if (log.mLevel.warningOrAbove()) {
            // Parse the exceptions in the entry



            // Put into corresponding entry collections
            Map<String, Queue<LogEntry>> node2Entries = findMap(log.mLevel);
            node2Entries.putIfAbsent(hostname, new ConcurrentLinkedQueue<>());
            node2Entries.get(hostname).add(log);
          } else {
            // The entries are related to primacy changes
            primacy.add(new ImmutablePair<>(hostname, log));
          }
        }
      }
    }

    // Parsed all logs, the log entries should be in the corresponding data structures

    // Report
    generateReports();

    // TODO: what to return?
  }


  private static boolean isAlluxioLog(File f) {
    String filename = f.getName();
    for (String p : FILE_NAMES_PREFIXES) {
      if (filename.startsWith(p)) {
        return true;
      }
    }
    System.out.format("File %s is not recognized%n", filename);
    return false;
  }

  // TODO
  private static void generateReports() {
    System.out.format("There are %s fatals, %s errors and %s warnings from all nodes%n",
            sumAllNodes(fatals), sumAllNodes(errors), sumAllNodes(warnings));
    System.out.format("Primacy change timeline: %s", primacy);



  }

  private static int sumAllNodes(Map<String, Queue<LogEntry>> map) {
    return map.values().stream().mapToInt(Queue::size).sum();
  }

  private static Map<String, Queue<LogEntry>> findMap(LogLevel level) {
    switch (level) {
      case WARN:
        return warnings;
      case ERROR:
        return errors;
      case FATAL:
        return fatals;
      default:
        throw new IllegalArgumentException(String.format("LogEntries for level %s are not kept for each node", level));
    }
  }

  private static void walk(File dir) throws IOException {
    for (File f : dir.listFiles()) {
      if (f.isDirectory()) {
        walk(f);
      } else {
        System.out.println(f.getCanonicalPath());
      }
    }
  }

  public static File createTemporaryDirectory() {
    final File file = Files.createTempDir();

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        FileUtils.deleteDirectory(file);
      } catch (IOException e) {
        System.err.format("Failed to clean up %s : %s%n", file.getAbsolutePath(), e.toString());
      }
    }));
    return file;
  }
}

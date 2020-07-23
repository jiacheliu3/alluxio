/*
 * The Alluxio Open Foundation licenses this work under the Apache License, version 2.0
 * (the "License"). You may not use this work except in compliance with the License, which is
 * available at www.apache.org/licenses/LICENSE-2.0
 *
 * This software is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied, as more fully set forth in the License.
 *
 * See the NOTICE file distributed with this work for information regarding copyright ownership.
 */

package alluxio.cli.bundler.command;

import alluxio.client.file.FileSystemContext;
import alluxio.conf.PropertyKey;
import alluxio.exception.AlluxioException;
import alluxio.util.CommonUtils;

import jline.internal.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Command to collect Alluxio logs.
 * */
public class CollectLogCommand  extends AbstractCollectInfoCommand {
  public static final String COMMAND_NAME = "collectLog";
  private static final Logger LOG = LoggerFactory.getLogger(CollectLogCommand.class);
  public static final Set<String> FILE_NAMES = Stream.of(
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
      "task.log",
      "task.out",
      "user"
  ).collect(Collectors.toSet());
  // We tolerate the beginning of a log file to contain some rows that are not timestamped.
  // 30 is chosen because a YARN application log can have >20 rows in the beginning for
  // general information about a job.
  // The timestamped log entries start after this general information block.
  private static final int TRY_PARSE_LOG_ROWS = 30;

  // Preserves the order of iteration, we try the longer pattern before the shorter one
  private static final Map<String, Integer> FORMAT_TO_LEN = new LinkedHashMap<String, Integer>(){
    {
      put("yyyy-MM-dd HH:mm:ss,SSS", 23);
      put("yyyy-MM-dd HH:mm:ss", 19);
      put("yyyy-MM-dd HH:mm", 16);
      put("yy/MM/dd HH:mm:ss", 17);
      put("yy/MM/dd HH:mm", 14);
      put("yyyy-MM-dd'T'HH:mm:ss.SSSXX", 28);
      put("yyyy-MM-dd'T'HH:mm:ss", 19);
      put("yyyy-MM-dd'T'HH:mm", 16);
    }
  };
  private static final Map<String, DateTimeFormatter> STRING_TO_FORMATTER = initFormatters();

  private String mLogDirPath;
  private File mLogDir;
  private URI mLogDirUri;
  private Set<String> mIncludedPrefix;
  private Set<String> mExcludedPrefix;
  private LocalDateTime mStartTime;
  private LocalDateTime mEndTime;

  public static final String INCLUDE_OPTION_NAME = "include-logs";
  private static final Option INCLUDE_OPTION =
          Option.builder().required(false).argName("filename-prefixes")
                  .longOpt(INCLUDE_OPTION_NAME).hasArg(true)
                  .desc("extra log file name prefixes to include in ${ALLUXIO_HOME}/logs. "
                          + "The files that start with the prefix will be included.").build();
  public static final String EXCLUDE_OPTION_NAME = "exclude-logs";
  private static final Option EXCLUDE_OPTION =
          Option.builder().required(false).argName("filename-prefixes")
                  .longOpt(EXCLUDE_OPTION_NAME).hasArg(true)
                  .desc("extra log file name prefixes to exclude in ${ALLUXIO_HOME}/logs. "
                          + "The files that start with the prefix will be excluded.").build();
  private static final String START_OPTION_NAME = "start-time";
  private static final Option START_OPTION =
          Option.builder().required(false).argName("datetime")
                  .longOpt(START_OPTION_NAME).hasArg(true)
                  .desc("logs that do not contain entries after this time will be ignored")
                  .build();
  private static final String END_OPTION_NAME = "end-time";
  private static final Option END_OPTION =
          Option.builder().required(false).argName("datetime")
                  .longOpt(END_OPTION_NAME).hasArg(true)
                  .desc("logs that do not contain entries before this time will be ignored")
                  .build();
  // Class specific options are aggregated into CollectInfo with reflection
  public static final Options OPTIONS = new Options().addOption(INCLUDE_OPTION)
          .addOption(EXCLUDE_OPTION).addOption(START_OPTION).addOption(END_OPTION);

  private static Map<String, DateTimeFormatter> initFormatters() {
    Map<String, DateTimeFormatter> strToFormatters = new HashMap<>();
    for (String s : FORMAT_TO_LEN.keySet()) {
      strToFormatters.put(s, DateTimeFormatter.ofPattern(s));
    }
    return strToFormatters;
  }

  /**
   * Creates a new instance of {@link CollectLogCommand}.
   *
   * @param fsContext the {@link FileSystemContext} to execute in
   * */
  public CollectLogCommand(FileSystemContext fsContext) {
    super(fsContext);
    mLogDirPath = fsContext.getClusterConf().get(PropertyKey.LOGS_DIR);
    mLogDir = new File(mLogDirPath);
    mLogDirUri = mLogDir.toURI();
  }

  @Override
  public String getCommandName() {
    return COMMAND_NAME;
  }

  @Override
  public boolean hasSubCommand() {
    return false;
  }

  @Override
  public int run(CommandLine cl) throws AlluxioException, IOException {
    // Determine the working dir path
    mWorkingDirPath = getWorkingDirectory(cl);

    // TODO(jiacheng): phase 2 Copy intelligently find security risks
    mIncludedPrefix = new HashSet<>(FILE_NAMES);
    // Define include list and exclude list
    if (cl.hasOption(INCLUDE_OPTION_NAME)) {
      Set<String> toInclude = parseFileNames(cl.getOptionValue(INCLUDE_OPTION_NAME));
      System.out.format("Include the following filename prefixes: %s%n", toInclude);
      mIncludedPrefix.addAll(toInclude);
    }
    if (cl.hasOption(EXCLUDE_OPTION_NAME)) {
      mExcludedPrefix = parseFileNames(cl.getOptionValue(EXCLUDE_OPTION_NAME));
      System.out.format("Exclude the following filename prefixes: %s%n", mExcludedPrefix);
    }

    // Check file timestamps
    boolean checkTimeStamp = false;
    if (cl.hasOption(START_OPTION_NAME)) {
      String startTimeStr = cl.getOptionValue(START_OPTION_NAME);
      mStartTime = parseDateTime(startTimeStr);
      System.out.format("Time window start: %s%n", mStartTime);
      checkTimeStamp = true;
    }
    if (cl.hasOption(END_OPTION_NAME)) {
      String endTimeStr = cl.getOptionValue(END_OPTION_NAME);
      mEndTime = parseDateTime(endTimeStr);
      System.out.format("Time window end: %s%n", mEndTime);
      checkTimeStamp = true;
    }
    if (mStartTime != null && mEndTime != null && mStartTime.isAfter(mEndTime)) {
      System.err.format("ERROR: Start time %s is later than end time %s!%n",
              mStartTime, mEndTime);
    }

    if (!mLogDir.exists()) {
      System.err.format("ERROR: Alluxio log directory %s does not exist!%n", mLogDirPath);
      return -1;
    }

    List<File> allFiles = CommonUtils.recursiveListDir(mLogDir);
    for (File f : allFiles) {
      String relativePath = getRelativePathToLogDir(f);
      try {
        if (!shouldCopy(f, relativePath, checkTimeStamp)) {
          continue;
        }
        File targetFile = new File(mWorkingDirPath, relativePath);
        FileUtils.copyFile(f, targetFile, true);
      } catch (IOException e) {
        System.err.format("ERROR: file %s not found %s%n", f.getCanonicalPath(), e.getMessage());
      }
    }

    return 0;
  }

  private String getRelativePathToLogDir(File f) {
    return mLogDirUri.relativize(f.toURI()).getPath();
  }

  private boolean shouldCopy(File f, String relativePath, boolean checkTimeStamp)
          throws IOException {
    if (!fileNameIsWanted(relativePath)) {
      return false;
    }
    if (checkTimeStamp) {
      if (!fileTimeStampIsWanted(f)) {
        return false;
      }
    }
    return true;
  }

  private boolean fileNameIsWanted(String fileName) {
    if (mExcludedPrefix != null) {
      for (String x : mExcludedPrefix) {
        if (fileName.startsWith(x)) {
          return false;
        }
      }
    }
    for (String s : mIncludedPrefix) {
      if (fileName.startsWith(s)) {
        return true;
      }
    }
    return false;
  }

  private boolean fileTimeStampIsWanted(File f) throws IOException {
    long timestamp = f.lastModified();
    LocalDateTime fileEndTime =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());

    // Infer file start time by parsing the first bunch of rows
    LocalDateTime fileStartTime = inferFileStartTime(f);
    if (fileStartTime == null) {
      fileStartTime = LocalDateTime.MIN;
    }
    System.out.format("File %s [%s, %s]%n", f.getCanonicalPath(), fileStartTime, fileEndTime);

    // The file is earlier than the desired interval
    if (mStartTime != null && mStartTime.isAfter(fileEndTime)) {
      System.out.format("File %s [%s, %s] is complete before start time %s%n",
              f.getCanonicalPath(), fileStartTime, fileEndTime, mStartTime);
      return false;
    }
    // The file is later than the desired interval
    if (mEndTime != null && mEndTime.isBefore(fileStartTime)) {
      System.out.format("File %s [%s, %s] is created after end time %s%n",
              f.getCanonicalPath(), fileStartTime, fileEndTime, mEndTime);
      return false;
    }
    return true;
  }

  /**
   * Infer the starting time of a log file by parsing the log entries from the beginning.
   * It will try the first certain lines with various known datetime patterns.
   *
   * @param f log file
   * @return the parsed datetime
   * */
  public static LocalDateTime inferFileStartTime(File f) throws FileNotFoundException {
    int r = 0;
    try (Scanner scanner = new Scanner(f)) {
      while (scanner.hasNextLine() && r < TRY_PARSE_LOG_ROWS) {
        String line = scanner.nextLine();
        LocalDateTime datetime = parseDateTime(line);
        if (datetime != null) {
          return datetime;
        }
        r++;
      }
    }
    return null;
  }

  private Set<String> parseFileNames(String input) {
    Set<String> names = new HashSet<>();
    names.addAll(Stream.of(input.split(",")).map(String::trim).collect(Collectors.toList()));
    return names;
  }

  @Override
  public String getUsage() {
    return "collectLogs <outputPath>";
  }

  @Override
  public String getDescription() {
    return "Collect Alluxio log files";
  }

  /**
   * Identifies the datetime from a certain piece of log by trying various known patterns.
   * Returns null if unable to identify a datetime.
   *
   * @param s a log entry
   * @return identified datetime
   * */
  @Nullable
  public static LocalDateTime parseDateTime(String s) {
    for (Map.Entry<String, Integer> entry : FORMAT_TO_LEN.entrySet()) {
      String f = entry.getKey();
      int len = entry.getValue();
      DateTimeFormatter fmt = STRING_TO_FORMATTER.get(f);
      try {
        if (s.length() < len) {
          continue;
        }
        String datePart = s.substring(0, len);
        LocalDateTime datetime = LocalDateTime.parse(datePart, fmt);
        return datetime;
      } catch (DateTimeParseException e) {
        // It just means the string is not in this format
        continue;
      }
    }
    // Unknown format here
    LOG.warn("Unknown date format in {}", s.length() > 50 ? s.substring(0, 50) : s);
    return null;
  }
}

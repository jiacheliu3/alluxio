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

import alluxio.cli.Command;
import alluxio.cli.CommandUtils;
import alluxio.client.file.FileSystemContext;
import alluxio.conf.InstancedConfiguration;
import alluxio.exception.status.InvalidArgumentException;
import alluxio.util.ConfigurationUtils;

import org.apache.commons.cli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * Abstraction of a command under InfoCollector.
 * */
public abstract class AbstractInfoCollectorCommand implements Command {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractInfoCollectorCommand.class);
  private static final String FILE_NAME_SUFFIX = ".txt";

  protected FileSystemContext mFsContext;
  protected String mWorkingDirPath;

  /**
   * Creates an instance of {@link AbstractInfoCollectorCommand}.
   *
   * @param fsContext {@link FileSystemContext} the context to run in
   * */
  public AbstractInfoCollectorCommand(@Nullable FileSystemContext fsContext) {
    if (fsContext == null) {
      fsContext =
              FileSystemContext.create(new InstancedConfiguration(ConfigurationUtils.defaults()));
    }
    mFsContext = fsContext;
  }

  @Override
  public void validateArgs(CommandLine cl) throws InvalidArgumentException {
    CommandUtils.checkNumOfArgsEquals(this, cl, 1);
  }

  /**
   * Gets the directory that this command should output to.
   * Creates the directory if it does not exist.
   *
   * @param cl the parsed {@link CommandLine}
   * @return the directory path
   * */
  public String getWorkingDirectory(CommandLine cl) {
    String[] args = cl.getArgs();
    String baseDirPath = args[0];
    String workingDirPath =  Paths.get(baseDirPath, this.getCommandName()).toString();
    System.out.println(String.format("Command %s works in %s", this.getCommandName(),
            workingDirPath));
    // mkdirs checks existence of the path
    File workingDir = new File(workingDirPath);
    workingDir.mkdirs();
    return workingDirPath;
  }

  /**
   * Generates the output file for the command to write printouts to.
   *
   * @param workingDirPath the base directory this command should output to
   * @param fileName name of the output file
   * @return the output file
   * */
  public File generateOutputFile(String workingDirPath, String fileName) throws IOException {
    if (!fileName.endsWith(FILE_NAME_SUFFIX)) {
      fileName += FILE_NAME_SUFFIX;
    }
    String outputFilePath = Paths.get(workingDirPath, fileName).toString();
    File outputFile = new File(outputFilePath);
    if (!outputFile.exists()) {
      outputFile.createNewFile();
    }
    return outputFile;
  }
}

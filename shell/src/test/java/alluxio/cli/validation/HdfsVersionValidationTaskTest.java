package alluxio.cli.validation;

import alluxio.cli.ValidateUtils;
import alluxio.conf.InstancedConfiguration;
import alluxio.conf.PropertyKey;
import alluxio.util.ShellUtils;
import com.google.common.collect.ImmutableMap;
import org.apache.http.impl.auth.NTLMScheme;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;


import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

@RunWith(PowerMockRunner.class)
@PrepareForTest(ShellUtils.class)
public class HdfsVersionValidationTaskTest {
  private static InstancedConfiguration sConf;

  @BeforeClass
  public static void initConf() throws IOException {
    sConf = InstancedConfiguration.defaults();
  }

  @Test
  public void versionNotMatchedDefault() throws Exception {
    PowerMockito.mockStatic(ShellUtils.class);
    String[] cmd = new String[]{"hadoop", "version"};
    BDDMockito.given(ShellUtils.execCommand(cmd)).willReturn("Hadoop 2.2");

    HdfsVersionValidationTask task = new HdfsVersionValidationTask(sConf);
    ValidateUtils.TaskResult result = task.validate(ImmutableMap.of());
    assertEquals(ValidateUtils.State.FAILED, result.getState());
    assertThat(result.getResult(), containsString("2.2 does not match alluxio.underfs.version"));
    assertThat(result.getAdvice(), containsString("configure alluxio.underfs.version"));
  }

  @Test
  public void versionNotMatched() throws Exception {
    PowerMockito.mockStatic(ShellUtils.class);
    String[] cmd = new String[]{"hadoop", "version"};
    BDDMockito.given(ShellUtils.execCommand(cmd)).willReturn("Hadoop 2.7");
    sConf.set(PropertyKey.UNDERFS_VERSION, "2.6");

    HdfsVersionValidationTask task = new HdfsVersionValidationTask(sConf);
    ValidateUtils.TaskResult result = task.validate(ImmutableMap.of());
    System.out.println(result);
    assertEquals(ValidateUtils.State.FAILED, result.getState());
    assertThat(result.getResult(), containsString("2.7 does not match alluxio.underfs.version=2.6"));
    assertThat(result.getAdvice(), containsString("configure alluxio.underfs.version"));
  }

  @Test
  public void versionMatched() throws Exception {
    PowerMockito.mockStatic(ShellUtils.class);
    String[] cmd = new String[]{"hadoop", "version"};
    BDDMockito.given(ShellUtils.execCommand(cmd)).willReturn("Hadoop 2.6");
    sConf.set(PropertyKey.UNDERFS_VERSION, "2.6");

    HdfsVersionValidationTask task = new HdfsVersionValidationTask(sConf);
    ValidateUtils.TaskResult result = task.validate(ImmutableMap.of());
    System.out.println(result);
    assertEquals(ValidateUtils.State.OK, result.getState());
  }

  @Test
  public void minorVersionAccepted() throws Exception {
    PowerMockito.mockStatic(ShellUtils.class);
    String[] cmd = new String[]{"hadoop", "version"};
    // The minor version is not defined in Alluxio, which should work
    BDDMockito.given(ShellUtils.execCommand(cmd)).willReturn("Hadoop 2.6.2");
    sConf.set(PropertyKey.UNDERFS_VERSION, "2.6");

    HdfsVersionValidationTask task = new HdfsVersionValidationTask(sConf);
    ValidateUtils.TaskResult result = task.validate(ImmutableMap.of());
    assertEquals(ValidateUtils.State.OK, result.getState());
  }

  @Test
  public void minorVersionConflict() throws Exception {
    PowerMockito.mockStatic(ShellUtils.class);
    String[] cmd = new String[]{"hadoop", "version"};
    // Alluxio defines a different minor version, which should not work
    BDDMockito.given(ShellUtils.execCommand(cmd)).willReturn("Hadoop 2.6.2");
    sConf.set(PropertyKey.UNDERFS_VERSION, "2.6.3");

    HdfsVersionValidationTask task = new HdfsVersionValidationTask(sConf);
    ValidateUtils.TaskResult result = task.validate(ImmutableMap.of());
    assertEquals(ValidateUtils.State.FAILED, result.getState());
    assertThat(result.getResult(), containsString("Hadoop version 2.6.2 does not match alluxio.underfs.version=2.6.3"));
  }

  @Test
  public void versionParsing() {
    String versionStr = "Hadoop 2.7.2\n" +
    "Subversion https://git-wip-us.apache.org/repos/asf/hadoop.git -r b165c4fe8a74265c792ce23f546c64604acf0e41\n" +
    "Compiled by jenkins on 2016-01-26T00:08Z\n" +
    "Compiled with protoc 2.5.0\n" +
    "From source with checksum d0fda26633fa762bff87ec759ebe689c\n" +
    "This command was run using /tmp/hadoop/share/hadoop/common/hadoop-common-2.7.2.jar";

    HdfsVersionValidationTask task = new HdfsVersionValidationTask(sConf);
    String version = task.parseVersion(versionStr);
    System.out.println(version);
  }

  @After
  public void resetConfig() {
    sConf.unset(PropertyKey.UNDERFS_VERSION);
  }
}

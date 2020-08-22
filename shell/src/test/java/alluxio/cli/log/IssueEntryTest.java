package alluxio.cli.log;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

public class IssueEntryTest {
  @Test
  public void extractTemplate() throws Exception {
    String s = "inodeId {} does not exist; too many retries";
    String p = s.replaceAll("\\{.*\\}", ".*");
    System.out.format("Extracted template %s%n", p);
    Pattern pattern = Pattern.compile(p);

    // This should match the template
    String filled = "inodeId 10113188364287 does not exist; too many retries";
    Matcher m = pattern.matcher(filled);
    assertTrue(m.matches());
    System.out.println(m.group());
  }
}

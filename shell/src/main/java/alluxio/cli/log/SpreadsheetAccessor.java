package alluxio.cli.log;

  import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.BufferedReader;
  import java.io.FileReader;
import java.io.IOException;
  import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// Ref: https://developers.google.com/sheets/api/quickstart/java
public class SpreadsheetAccessor {
    private static final String APPLICATION_NAME = "Google Sheets API Java Quickstart";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";



  /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY);
  private static final String CREDENTIALS_FILE_PATH = "/Users/jiachengliu/credentials/credentials.json";

  private static final String SPREADSHEET_ID = "1HM2SCfJ9xDl25zKlVsKzg9tdqp3CBj7S6MX4cWDsQQs";
  private static final String RANGE = "Issues!A:Q";

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
      // Load client secrets.
      GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new BufferedReader(new FileReader(CREDENTIALS_FILE_PATH)));

      // Build flow and trigger user authorization request.
      GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
              HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
              .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
              .setAccessType("offline")
              .build();
      LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
      return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    /**
     * Prints the names and majors of students in a sample spreadsheet:
     * https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit
     */
    public static void main(String... args) throws IOException, GeneralSecurityException, IllegalAccessException {
      // Build a new authorized API client service.
      final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
      Sheets service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
              .setApplicationName(APPLICATION_NAME)
              .build();

      ValueRange response = service.spreadsheets().values()
              .get(SPREADSHEET_ID, RANGE)
              .execute();

      System.out.format("range %s%n", response.getRange());
      System.out.format("major dimension %s%n", response.getMajorDimension());

      List<List<Object>> values = response.getValues();
      if (values == null || values.isEmpty()) {
        System.out.println("No data found.");
        return;
      }

      // Enumerate the rows and parse into IssueEntry objects
      List<IssueEntry> entries = new ArrayList<>();
      for (int i = 0; i < values.size(); i++) {
        // Skip the first 2 rows as they are the title
        if (i < 2) {
          continue;
        }

        List<Object> row = values.get(i);
        System.out.println("Row length: " + row.size());

        if (row.size() < 2) {
          continue;
        }
        IssueEntry entry = createNewIssueEntry(row);
        entries.add(entry);
      }

      // TODO: What do i do to verify
      return;
    }

    public static IssueEntry createNewIssueEntry(List<Object> row) throws IllegalAccessException {
      // This is ordered by the order of declaration
      Field[] fields = IssueEntry.class.getDeclaredFields();
      System.out.println("There are " + fields.length + " fields: " + Arrays.toString(fields));

      // The empty cells are ignored(not included in the response) if there are no non-empty cells after them
      // This is why the rows have different lengths
      int i = 0;
      IssueEntry entry = new IssueEntry();
      for (Object o : row) {
        String s = (String) o;
        fields[i].set(entry, s);
        i++;
      }

      // Parse the templates
      entry.processTemplates();

      return entry;
    }

  }

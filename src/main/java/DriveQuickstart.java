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
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

public class DriveQuickstart {
    private static final String APPLICATION_NAME = "Google Drive API Java Quickstart";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = DriveQuickstart.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receier = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receier).authorize("user");
    }

    public static void main(String... args) throws IOException, GeneralSecurityException {
        // Build a new authorized API client service.
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

        if (args.length != 2) {
            System.out.println("No files found.");
            System.exit(0);
        }

        String srcId = args[0];
        String destId = args[1];

        copy(service, srcId, destId);

//         // Print the names and IDs for up to 10 files.
//         FileList result = service.files().list()
//                 .setQ("'1sqPIgFQ3nf18eNbdu1F6YSN690wwMq7A' in parents")
//                 .setSupportsTeamDrives(true)
//                 .setIncludeTeamDriveItems(true)
// //                .setPageSize(10)
//                 .setFields("nextPageToken, files(id, name)")
//                 .execute();

//         List<File> files = result.getFiles();
//         if (files == null || files.isEmpty()) {
//             System.out.println("No files found.");
//         } else {
//             System.out.println("Files:");
//             for (File file : files) {
//                 System.out.printf("%s (%s)\n", file.getName(), file.getId());
//             }
//         }

        // File fileMetadata = new File();
        // fileMetadata.setParents(Collections.singletonList("0AGDbk3CZsXZJUk9PVA"));
        // fileMetadata.setName("Invoices");
        // fileMetadata.setMimeType("application/vnd.google-apps.folder");

        // File file = service.files().create(fileMetadata)
        //     .setSupportsTeamDrives(true)
        //     .setFields("id")
        //     .execute();
        // System.out.println("Folder ID: " + file.getId());
    }

    public static void copy(Drive drive, String srcId, String destId) throws IOException {
        System.out.printf("copy from:%s to:%s\n", srcId, destId);

        FileList result = drive.files().list()
            .setQ("'" + srcId + "' in parents")
            .setSupportsTeamDrives(true)
            .setIncludeTeamDriveItems(true)
            .setFields("nextPageToken, files(id, name, mimeType)")
            .execute();

        List<File> files = result.getFiles();
        if (files == null || files.isEmpty()) {
            System.out.println("No files found.");
            return;
        } else {
            for (File file : files) {
                System.out.printf("from object: %s\n", file.getMimeType());
                if ("application/vnd.google-apps.folder".equals(file.getMimeType())) {
                    File fileMetadata = new File();
                    fileMetadata.setName(file.getName());
                    fileMetadata.setMimeType("application/vnd.google-apps.folder");
                    fileMetadata.setParents(Collections.singletonList(destId));

                    File folder = drive.files().create(fileMetadata)
                        .setSupportsTeamDrives(true)
                        .setFields("id")
                        .execute();

                    System.out.printf("create folder: %s\n", folder.getId());
                    copy(drive, file.getId(), folder.getId());
                } else {
                    File fileMetadata = new File();
                    fileMetadata.setName(file.getName());
                    fileMetadata.setParents(Collections.singletonList(destId));

                    drive.files().copy(file.getId(), fileMetadata)
                        .setSupportsTeamDrives(true)
                        .execute();

                    System.out.printf("copy file: %s\n", file.getId());
                }
            }
        }
    }
    public static void copyFile(Drive drive, String srcId, String destId) throws Exception {
        FileList result = drive.files().list()
            .setQ("'" + srcId + "' in parents")
            .setSupportsTeamDrives(true)
            .setIncludeTeamDriveItems(true)
            .setFields("nextPageToken, files(id, name)")
            .execute();

        List<File> files = result.getFiles();
        if (files == null || files.isEmpty()) {
            System.out.println("No files found.");
        } else {
            System.out.println("Files:");
            for (File file : files) {
                System.out.printf("%s (%s)\n", file.getName(), file.getId());
            }
        }
    }
}
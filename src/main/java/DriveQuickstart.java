import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSink;
import com.google.common.io.Files;

import org.apache.commons.io.FileUtils;

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

    private static Map<String, String> fileMaps = new HashMap<String, String>();

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

        // 結果ファイルがあるか確認し、なければ作成する
        // Read the lines of a UTF-8 text file
        List<String> lines = FileUtils.readLines(new java.io.File("result.tsv"), "utf-8");
        lines.stream().forEach(line -> {
            String[] value = line.split("\t");
            fileMaps.put(value[0], value[1]);
        });

        if (args.length != 2) {
            System.out.println("No files found.");
            System.exit(0);
        }

        String srcId = args[0];
        String destId = args[1];

        copy(service, srcId, destId);
    }

    public static void copy(Drive drive, String srcId, String destId) throws IOException {
        FileList result = drive.files().list()
            .setQ("'" + srcId + "' in parents")
            .setSupportsTeamDrives(true)
            .setIncludeTeamDriveItems(true)
            .setFields("nextPageToken, files(id, name, mimeType)")
            .execute();

        List<File> files = result.getFiles();
        if (files == null || files.isEmpty()) {
            return;
        } else {
            for (File file : files) {
                if ("application/vnd.google-apps.folder".equals(file.getMimeType())) {

                    File fileMetadata = new File();
                    fileMetadata.setName(file.getName());
                    fileMetadata.setMimeType("application/vnd.google-apps.folder");
                    fileMetadata.setParents(Collections.singletonList(destId));

                    try {
                        String nextId = fileMaps.get(file.getId());
                        if(nextId != null) {
                            System.out.printf("SKIP\t%s\t%s\t%s\n", file.getName(), file.getId(), nextId);
                        } else {
                            File folder = drive.files().create(fileMetadata)
                            .setSupportsTeamDrives(true)
                            .setFields("id")
                            .execute();
    
                            nextId = folder.getId();
                            System.out.printf("OK\t%s\t%s\t%s\n", file.getName(), file.getId(), nextId);
                            append(fileMaps, file.getId(), nextId);
                        }
                        copy(drive, file.getId(), nextId);
                    } catch(Exception e) {
                        e.printStackTrace();
                        System.out.printf("NG\t%s\t%s\n", file.getName(), file.getId());
                    }
                } else {
                    String nextId = fileMaps.get(file.getId());
                    if (nextId != null) {
                        System.out.printf("SKIP\t%s\t%s\t%s\n", file.getName(), file.getId(), nextId);
                        continue;
                    }

                    File fileMetadata = new File();
                    fileMetadata.setName(file.getName());
                    fileMetadata.setParents(Collections.singletonList(destId));

                    try {
                        File copyFile = drive.files().copy(file.getId(), fileMetadata)
                        .setSupportsTeamDrives(true)
                        .setFields("id")
                        .execute();

                        System.out.printf("OK\t%s\t%s\t%s\n", file.getName(), file.getId(), copyFile.getId());
                        append(fileMaps, file.getId(), copyFile.getId());
                    } catch(Exception e) {
                        e.printStackTrace();
                        System.out.printf("NG\t%s\t%s\n", file.getName(), file.getId());
                    }
                }
            }
        }
    }

    public static boolean contains(Map map, String key) {
        return map.containsKey(key);
    }

    public static void append(Map map, String key, String value) throws IOException {
        map.put(key, value);
        List<String> lines = new ArrayList<>();
        map.forEach((k, v) -> lines.add(k + "\t" + v));
        FileUtils.writeLines(new java.io.File("result.tsv"), lines);
    }
}
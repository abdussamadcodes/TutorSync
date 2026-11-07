package com.abdussamadcodes.tutorsync.util;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

public class GoogleDriveBackupHelper {

    private final Drive driveService;

    public GoogleDriveBackupHelper(@NonNull Context context, @NonNull GoogleSignInAccount account) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context.getApplicationContext(),
                Collections.singleton(DriveScopes.DRIVE_APPDATA)
        );
        credential.setSelectedAccount(account.getAccount());

        driveService = new Drive.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
        ).setApplicationName("TutorSync").build();
    }

    /**
     * Uploads a local backup file to the hidden Google Drive AppData folder.
     * Keeps only the 3 most recent backups to conserve user storage.
     * Never deletes the previous backup until the new upload succeeds.
     */
    public void uploadBackup(@NonNull java.io.File localFile, @NonNull String mimeType) throws IOException {
        // 1. Upload the new backup FIRST
        File fileMetadata = new File();
        fileMetadata.setName(localFile.getName());
        fileMetadata.setParents(Collections.singletonList("appDataFolder"));

        FileContent mediaContent = new FileContent(mimeType, localFile);
        driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute();

        // 2. Clean up old cloud backups (Max backups: 3)
        // This only runs if step 1 succeeded.
        List<File> existingBackups = listBackups();
        if (existingBackups != null && existingBackups.size() > 3) {
            for (int i = 3; i < existingBackups.size(); i++) {
                try {
                    driveService.files().delete(existingBackups.get(i).getId()).execute();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Lists all TutorSync backups stored in the hidden AppData folder, sorted newest first.
     */
    public List<File> listBackups() throws IOException {
        FileList result = driveService.files().list()
                .setSpaces("appDataFolder")
                .setOrderBy("createdTime desc")
                .setFields("files(id, name, createdTime, size)")
                .execute();
        return result.getFiles();
    }

    public void downloadBackup(@NonNull String fileId, @NonNull java.io.File targetFile) throws IOException {
        try (OutputStream out = new FileOutputStream(targetFile)) {
            driveService.files().get(fileId).executeMediaAndDownloadTo(out);
        }
    }
}
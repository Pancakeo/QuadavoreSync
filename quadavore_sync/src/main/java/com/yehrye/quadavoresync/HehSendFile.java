package com.yehrye.quadavoresync;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HehSendFile {
    private MainActivity m_activity;
    private Context m_context;

    HehSendFile(MainActivity activity) {
        m_activity = activity;
        m_context = activity.getApplicationContext();
    }

    void sendFile(final File[] files, final int current) {
        if (current >= files.length) {
            m_activity.appendStatus("Done uploading!");
            Log.d("Quadavore", "done uploading batch");
            return;
        }

        final File file = files[current];
        String content = HehUtil.convertStreamToString(file);
        m_activity.appendStatus("Uploading " + file.getName());

        // Compression.
        File outputDir = m_context.getCacheDir(); // context being the Activity pointer
        File outputFile;

        try {
            byte[] compressed = HehUtil.compress(content);

            outputFile = File.createTempFile("upload", ".csv", outputDir);
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(compressed);
            fos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // TODO will need a way to clean up temp files (if app was closed prematurely or something).
        final File compressedFile = outputFile;
        final String originalFileName = file.getName();

        Log.d("Quadavore", "Uploading file " + file.getName() + ", size: " + file.length());
        Ion.with(m_activity)
                .load("PUT", m_activity.getServer() + "/flight_log")
                .setTimeout(1000 * 60 * 5)     // 5 minutes
                .setMultipartParameter("user_id", m_activity.getToken())
                .setMultipartParameter("user_name", "Android Thing")
                .setMultipartParameter("file_name", file.getName())
                .setMultipartParameter("is_gzip", "yup")
                .setMultipartFile("uploaded_file", compressedFile)
                .asJsonObject()
                .setCallback(new FutureCallback<JsonObject>() {
                    @Override
                    public void onCompleted(Exception e, JsonObject result) {
                        Log.d("Quadavore", file.getName() + " has completed.");

                        if (e == null) {
                            Log.d("Quadavore", "Uploaded probably worked: " + result);
                            m_activity.appendStatus("Probably uploaded " + originalFileName);

                            ContentValues values = new ContentValues();
                            values.put("log_name", originalFileName);

                            m_activity.getDb().insert("uploaded_logs", null, values);
                            if (compressedFile.delete()) {
                                Log.d("Quadavore", "Successfully deleted temp file " + compressedFile);
                            }
                            Toast.makeText(m_context, "Uploaded " + file.getName(), Toast.LENGTH_SHORT).show();
                            sendFile(files, current + 1);
                        } else {
                            Log.d("Quadavore", "Uploaded probably failed: " + result);
                            Toast.makeText(m_context, "Failure " + e.toString(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    void uploadLogs(String logPath, FileFilter fileFilter) {
        File quadLogs = new File(Environment.getExternalStorageDirectory(), logPath);
        Log.d("Quadavore", "Log path " + logPath + " is directory: " + quadLogs.isDirectory());

        if (!quadLogs.isDirectory()) {
            m_activity.appendStatus("Not a directory: " + logPath);
        } else {
            File[] files = quadLogs.listFiles(fileFilter);

            List<File> filesToSend = new ArrayList<>();
            String[] columns = new String[]{"log_name"};
            Cursor c = m_activity.getDb().query("uploaded_logs", columns, null, null, null, null, null);

            List<String> filesSent = new ArrayList<>();
            c.moveToFirst();
            while (!c.isAfterLast()) {
                String log_name = c.getString(0);
                filesSent.add(log_name);
                Log.d("Quadavore", "wup: " + log_name);
                c.moveToNext();
            }
            c.close();

            for (File f : files) {
                if (!filesSent.contains(f.getName())) {
                    filesToSend.add(f);
                }
            }

            files = filesToSend.toArray(new File[filesToSend.size()]);

            if (files.length > 0) {
                m_activity.appendStatus("Uploading " + files.length + " file(s)");
                sendFile(files, 0);
            } else {
                m_activity.appendStatus("No new files to upload.");
                Toast.makeText(m_context, "No new files to upload", Toast.LENGTH_SHORT).show();
            }

        }

    }
}

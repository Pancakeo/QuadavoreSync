package com.yehrye.quadavoresync;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private Button m_submitLogs;
    private SharedPreferences m_sharedPreferences;
    private String m_userToken;
    private String m_server = "http://yehrye.com:1991";
    private SQLiteDatabase m_db;
    private TextView m_status;

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();

        return Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case 42: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    goodToGoProbably();
                    Log.d("Quadavore", "PERMISSION GRANTED");
                } else {
                    Log.d("Quadavore", "Uh oh");
                }
            }
        }
    }

    void goodToGoProbably() {
        if (isExternalStorageReadable()) {
            m_submitLogs.setEnabled(true);
        } else {
            Log.d("Quadavore", "Storage isn't readable for some reason.");
        }
    }

    void requestPermissions() {
        final Activity thisActivity = this;

        if (ContextCompat.checkSelfPermission(thisActivity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(thisActivity, Manifest.permission.READ_EXTERNAL_STORAGE)) {

                new AlertDialog.Builder(this)
                        .setTitle("Permission Rationale")
                        .setMessage("Storage is disabled. Maybe enable it?")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(thisActivity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 42);
                            }
                        })
                        .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // Do nothing
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            } else {

                ActivityCompat.requestPermissions(thisActivity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 42);
            }
        } else {
            goodToGoProbably();
        }
    }

    void sendFile(final File[] files, final int current) {
        if (current >= files.length) {
            m_status.append("Done uploading!\n");
            Log.d("Quadavore", "done uploading batch");
            return;
        }

        final File file = files[current];
        String content = HehUtil.convertStreamToString(file);

        // Compression.
        File outputDir = getApplicationContext().getCacheDir(); // context being the Activity pointer
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
        Ion.with(this)
                .load("PUT", m_server + "/flight_log")
                .setTimeout(1000 * 60 * 5)     // 5 minutes
                .setMultipartParameter("user_id", m_userToken)
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
                            m_status.append("Probably uploaded " + originalFileName + "\n");

                            ContentValues values = new ContentValues();
                            values.put("log_name", originalFileName);

                            m_db.insert("uploaded_logs", null, values);
                            if (compressedFile.delete()) {
                                Log.d("Quadavore", "Successfully deleted temp file " + compressedFile);
                            }
                            Toast.makeText(getApplicationContext(), "Uploaded " + file.getName(), Toast.LENGTH_SHORT).show();
                            sendFile(files, current + 1);
                        } else {
                            Log.d("Quadavore", "Uploaded probably failed: " + result);
                            Toast.makeText(getApplicationContext(), "Failure " + e.toString(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    void uploadLogs(String logPath, FileFilter fileFilter) {
        File quadLogs = new File(Environment.getExternalStorageDirectory(), logPath);
        Log.d("Quadavore", "Log path " + logPath + " is directory: " + quadLogs.isDirectory());

        if (quadLogs.isDirectory()) {
            File[] files = quadLogs.listFiles(fileFilter);

            List<File> filesToSend = new ArrayList<>();
            String[] columns = new String[]{"log_name"};
            Cursor c = m_db.query("uploaded_logs", columns, null, null, null, null, null);

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
                m_status.append("Uploading " + files.length + " file(s).\n");
                sendFile(files, 0);
            } else {
                m_status.append("No new files to upload.");
                Toast.makeText(getApplicationContext(), "No new files to upload", Toast.LENGTH_SHORT).show();
            }

        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        HehStorage savedLogs = new HehStorage(getApplicationContext());
        m_db = savedLogs.getReadableDatabase();

        m_status = (TextView) findViewById(R.id.status);
        m_status.append("Hello! Status shows up here.\n");

        m_sharedPreferences = getSharedPreferences("quadavore.settings", Context.MODE_PRIVATE);
        String currentUserToken = m_sharedPreferences.getString("userToken", "");
        String currentServerOverride = m_sharedPreferences.getString("serverOverride", "");

        EditText userToken = (EditText) findViewById(R.id.userToken);
        userToken.setText(currentUserToken);

        final EditText serverOverride = (EditText) findViewById(R.id.serverOverride);
        serverOverride.setText(currentServerOverride);

        m_submitLogs = (Button) findViewById(R.id.submit_logs);

        // Request permissions, if needed.
        requestPermissions();

        final Spinner serverSpinner = (Spinner) findViewById(R.id.spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.server_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        serverSpinner.setAdapter(adapter);

        m_submitLogs.setTransformationMethod(null);  // avoid all caps.
        m_submitLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
                boolean isWiFi = isConnected && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;

                if (!isWiFi) {
                    Toast.makeText(getApplicationContext(), "Wifi connection required", Toast.LENGTH_SHORT).show();
                    return;
                }

                m_server = serverSpinner.getSelectedItem().toString();

                String serverOverrideValue = serverOverride.getText().toString().trim();
                if (serverOverrideValue.length() > 0) {
                    m_server = serverOverrideValue;
                }

                SharedPreferences.Editor editor = m_sharedPreferences.edit();
                EditText userToken = (EditText) findViewById(R.id.userToken);
                editor.putString("userToken", userToken.getText().toString().trim());
                editor.putString("serverOverride", serverOverrideValue);
                editor.apply();

                Log.d("Quadavore", m_server);

                m_userToken = userToken.getText().toString().trim();
                if (m_userToken.length() >= 3) {
                    uploadLogs("/LitchiApp/flightlogs", new FileFilter() {
                        @Override
                        public boolean accept(File pathname) {
                            return pathname.getPath().toLowerCase().contains(".csv");
                        }
                    });

//                    uploadLogs("DJI/dji.pilot/FlightRecord", new FileFilter() {
//                        @Override
//                        public boolean accept(File pathname) {
//                            return pathname.getPath().toLowerCase().contains(".txt");
//                        }
//                    });

                } else {
                    Toast.makeText(getApplicationContext(), "User token must at least 3 characters.", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

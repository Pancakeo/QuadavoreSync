package com.yehrye.quadavoresync;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Scanner;
import java.util.zip.GZIPOutputStream;

public class MainActivity extends AppCompatActivity {
    private Button m_submitLogs;
    private SharedPreferences m_sharedPreferences;
    private String m_userToken;
    private String m_server = "http://yehrye.com:1991";

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

    String convertStreamToString(File f) {

        try {
            InputStream is = new FileInputStream(f);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    byte[] compress(String string) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream(string.length());
        GZIPOutputStream gos = new GZIPOutputStream(os);
        gos.write(string.getBytes());
        gos.close();
        byte[] compressed = os.toByteArray();
        os.close();
        return compressed;
    }

    void sendFile(final File[] files, final int current) {
        if (current >= files.length) {
            Log.d("Quadavore", "done uploading batch");
            return;
        }

        final File file = files[current];
        String content = convertStreamToString(file);

        // Compression.
        File outputDir = getApplicationContext().getCacheDir(); // context being the Activity pointer
        File outputFile;

        try {
            byte[] compressed = compress(content);
            Log.d("Quadavore", "heh " + Arrays.toString(compressed));

            outputFile = File.createTempFile("upload", "csv", outputDir);
            FileOutputStream fos = new FileOutputStream(outputFile);
            fos.write(compressed);
            fos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // TODO will need a way to clean up temp files.
        final File compressedFile = outputFile;

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

            sendFile(files, 0);
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

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

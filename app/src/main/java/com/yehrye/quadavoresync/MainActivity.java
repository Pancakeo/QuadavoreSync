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
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

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

    public static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    public static String getStringFromFile(File file) throws Exception {
        FileInputStream fin = new FileInputStream(file);
        String ret = convertStreamToString(fin);
        //Make sure you close all streams.
        fin.close();
        return ret;
    }

    void processLitchiLogs() {
        File quadLogs = new File(Environment.getExternalStorageDirectory(), "/LitchiApp/flightlogs");
        Log.d("Quadavore", "Litchi file path: " + quadLogs.getAbsolutePath());

        if (quadLogs.isDirectory()) {
            Log.d("Quadavore", "Exists: " + quadLogs.exists());
            Log.d("Quadavore", "Found Litchi log folder.");

            File[] files = quadLogs.listFiles(new FileFilter() {
                @Override
                public boolean accept(File pathname) {
                    return pathname.getName().toLowerCase().contains(".csv");
                }
            });

            for (File file : files) {
                Log.d("Quadavore", "Uploading file " + file.getName() + ", size: " + file.length());

                try {
                    String content = getStringFromFile(file);

                    JsonObject json = new JsonObject();
                    json.addProperty("user_id", m_userToken);
                    json.addProperty("user_name", "Android Thing");
                    json.addProperty("csv_raw", content);
                    json.addProperty("file_name", file.getName());

                    final File fileRef = file;

                    Ion.with(this)
                            .load("PUT", m_server + "/flight_log")
                            .setJsonObjectBody(json)
                            .asJsonObject()
                            .setCallback(new FutureCallback<JsonObject>() {
                                @Override
                                public void onCompleted(Exception e, JsonObject result) {
                                    if (e == null) {
                                        Toast.makeText(getApplicationContext(), "Uploaded " + fileRef.getName(), Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(getApplicationContext(), "Failure " + e.toString(), Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });

                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
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

        EditText userToken = (EditText) findViewById(R.id.userToken);
        userToken.setText(currentUserToken);

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
                SharedPreferences.Editor editor = m_sharedPreferences.edit();
                EditText userToken = (EditText) findViewById(R.id.userToken);
                Log.d("Quadavore", userToken.getText().toString());
                editor.putString("userToken", userToken.getText().toString());
                editor.apply();

                m_server = serverSpinner.getSelectedItem().toString();
                Log.d("Quadavore", m_server);

                m_userToken = userToken.getText().toString().trim();
                if (m_userToken.length() >= 3) {
                    processLitchiLogs();
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

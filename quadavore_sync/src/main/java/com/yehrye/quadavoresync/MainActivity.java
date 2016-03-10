package com.yehrye.quadavoresync;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileFilter;

public class MainActivity extends AppCompatActivity {
    private Button m_submitLogs;
    private SharedPreferences m_sharedPreferences;
    private String m_userToken;
    private String m_server = "http://yehrye.com:1991";
    private SQLiteDatabase m_db;
    private TextView m_status;
    private MainActivity m_activity;

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
        if (HehUtil.isExternalStorageReadable()) {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        m_activity = this;

        HehStorage savedLogs = new HehStorage(getApplicationContext());
        m_db = savedLogs.getReadableDatabase();

        m_status = (TextView) findViewById(R.id.status);
        appendStatus("Hello! Status shows up here.");

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
                    HehSendFile wup = new HehSendFile(m_activity);
                    wup.uploadLogs("/LitchiApp/flightlogs", new FileFilter() {
                        @Override
                        public boolean accept(File pathname) {
                            return pathname.getPath().toLowerCase().contains(".csv");
                        }
                    });
                } else {
                    Toast.makeText(getApplicationContext(), "User token must at least 3 characters.", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    public void appendStatus(String msg) {
        m_status.append(msg + '\n');
    }

    public SQLiteDatabase getDb() {
        return m_db;
    }

    public String getToken() {
        return m_userToken;
    }

    public String getServer() {
        return m_server;
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

package com.yehrye.quadavoresync;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();

        return Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 42: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Quadavore", "PERMISSION GRANTED");
                } else {
                    Log.d("Quadavore", "Uh oh");
                }
            }
        }
    }

    void requestPermissions() {
        final Activity thisActivity = this;

        if (ContextCompat.checkSelfPermission(thisActivity,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(thisActivity, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Log.d("Quadavore", "wup - needs rationale");

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
                                // Okay then.
                            }
                        })
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
            } else {

                // No explanation needed, we can request the permission:
                ActivityCompat.requestPermissions(thisActivity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 42);
                Log.d("Quadavore", "wup - uhh...");
            }
        }
    }

    void readLitchiLogs() {
        File djiFile = new File(Environment.getExternalStorageDirectory(), "/LitchiApp/flightlogs");
        Log.d("Quadavore", "DJI file path: " + djiFile.getAbsolutePath());

        if (djiFile.isDirectory()) {
            Log.d("Quadavore", "Exists: " + djiFile.exists());
            Log.d("Quadavore", "Found DJI log path.");
            Log.d("Quadavore", "Herrrrg: " + djiFile.canRead());
            Log.d("Quadavore", "list: " + Arrays.toString(djiFile.list()));

            Toast.makeText(getApplicationContext(), "Heh: " + Arrays.toString(djiFile.listFiles()), Toast.LENGTH_SHORT).show();
//                    File[] files = djiFile.listFiles();
//                    File[] files = djiFile.listFiles(new FileFilter() {
//                        @Override
//                        public boolean accept(File pathname) {
//                            Log.d("Quadavore", pathname.getName().toLowerCase().contains(".txt") + "");
//                            return pathname.getName().toLowerCase().contains(".txt");
//                        }
//                    });

//                    for (File file : files) {
//                        Log.d("Quadavore", "dji " + file.getName());
//                    }
        }

    }

    void readDJILogs() {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Request permissions, if needed.
        requestPermissions();

        final Button but = (Button) findViewById(R.id.submit_logs);
        but.setTransformationMethod(null);  // avoid all caps.

        but.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                readLitchiLogs();
                readDJILogs();
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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

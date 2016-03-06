package com.yehrye.quadavoresync;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class HehStorage extends SQLiteOpenHelper {
    HehStorage(Context context) {
        super(context, "fancy", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Maybe add a pk?
        Log.d("Quadavore", "Heh!");
        db.execSQL("CREATE TABLE uploaded_logs (log_name TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}

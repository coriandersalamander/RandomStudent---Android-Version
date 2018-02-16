package com.lapharcius.randomstudent;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class studentsDatabase extends ContextWrapper {
    private static studentsDatabase instance;
    private static SQLiteDatabase database;

    private studentsDatabase(Context base)
    {
        super(base);
    }

    public static SQLiteDatabase getDatabase()
    {
        return database;
    }
    public static studentsDatabase getInstance(Context base)
    {
        if (instance == null)
        {
//            Context mContext = base;
            instance = new studentsDatabase(base);
        }

        return instance;
    }

    public void createDB() {
        String sql;

        try {
            database = openOrCreateDatabase("students.db", 0, null);
            sql = "CREATE TABLE IF NOT EXISTS students( _id INTEGER PRIMARY KEY AUTOINCREMENT, firstname TEXT, lastname TEXT, period TEXT)";
            database.execSQL(sql);
        } catch (SQLException e) {
            Log.i("LOGMESSAGE", "SQL Exception" + e);
        }
    }

    static void dump()
    {
        String sqlString = "SELECT * FROM students";
        Cursor cursor = database.rawQuery(sqlString, null);
        for (int i = 0; i < cursor.getColumnCount(); i++) {
            Log.i("LOGMESSAGE", "In studentsDatabase - Dump - " + cursor.getColumnNames()[i]);
        }
        cursor.moveToFirst();
        do
        {
            int numCols = cursor.getColumnCount();
            for (int i = 0; i < numCols; i++)
            {
                Log.i("LOGMESSAGE" , "In studentsDatabase - Dump - " + cursor.getString(i));

            }
        }
        while (cursor.moveToNext());
//        Log.i("LOGMESSAGE" , "In studentsDatabase - Dump - " + cursor.getColumnCount()
        cursor.close();
    }

    public int countItems(String aPeriod) {
        String sqlString;
        if (!aPeriod.equals(""))
        {
            sqlString = "SELECT * FROM students WHERE period = '" + aPeriod + "'";
        }
        else
        {
            sqlString = "SELECT * FROM students";
        }
        Cursor cursor = database.rawQuery(sqlString, null);
        int numItems = cursor.getCount();
        cursor.close();

        return (numItems);
    }

    public int countItems()
    {
        return countItems("");
    }

    public Cursor getAllEntries()
    {
        return getEntries("");
    }

    public Cursor getEntries(String aPeriod)
    {
        String sqlString;
        if (!aPeriod.equals(""))
        {
            sqlString = "SELECT * FROM students WHERE period = '" + aPeriod + "'";
        }
        else
        {
            sqlString = "SELECT * FROM students";
        }
        return database.rawQuery(sqlString, null);

    }

    public Cursor getPeriodEntries()
    {
        String sqlString;
        sqlString = "SELECT DISTINCT period FROM students";
        return database.rawQuery(sqlString, null);

    }

    void populateDBWithTempValues(String aPeriod)
    {
    }
    void populateDBWithSpreadsheet()
    {
    }
}

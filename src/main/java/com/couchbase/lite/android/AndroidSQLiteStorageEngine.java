/**
 * Created by Wayne Carter.
 *
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.lite.android;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Build;

import com.couchbase.lite.storage.ContentValues;
import com.couchbase.lite.storage.Cursor;
import com.couchbase.lite.storage.SQLException;
import com.couchbase.lite.storage.SQLiteStorageEngine;
import com.couchbase.lite.util.Log;

public class AndroidSQLiteStorageEngine implements SQLiteStorageEngine {
    public static final String TAG = "AndroidSQLiteStorageEngine";

    private SQLiteDatabase database;

    public AndroidSQLiteStorageEngine() {
    }

    @Override
    public boolean open(String path) {
        if(database != null && database.isOpen())
            return true;

        try {
            // Write-Ahead Logging (WAL) http://sqlite.org/wal.html
            // http://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.html#enableWriteAheadLogging()
            // ENABLE_WRITE_AHEAD_LOGGING is available from API 16
            // enableWriteAheadLogging() is available from API 11, but it does not work with API 9 and 10.
            // Minimum version CBL Android supports is API 9

            // NOTE: Not obvious difference. But it seems Without WAL is faster.
            //       WAL consumes more memory, it might make GC busier.
            database = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.CREATE_IF_NECESSARY);
            Log.v(Log.TAG_DATABASE, "%s: Opened Android sqlite db", this);

            // Register custom collator:
            String sqliteDatabaseClassName = "android/database/sqlite/SQLiteDatabase";
            SQLiteRevCollator.register(database, sqliteDatabaseClassName, Build.VERSION.SDK_INT);
            SQLiteJsonCollator.register(database, sqliteDatabaseClassName, Build.VERSION.SDK_INT);
        } catch(SQLiteException e) {
            Log.e(TAG, "Error opening", e);
            if (database != null)
                database.close();
            return false;
        }

        return database.isOpen();
    }

    @Override
    public int getVersion() {
        return database.getVersion();
    }

    @Override
    public void setVersion(int version) {
        database.setVersion(version);
    }

    @Override
    public boolean isOpen() {
        return database != null && database.isOpen();
    }

    @Override
    public void beginTransaction() {
        database.beginTransaction();
        // NOTE: Use beginTransactionNonExclusive() with ENABLE_WRITE_AHEAD_LOGGING
        //       http://stackoverflow.com/questions/8104832/sqlite-simultaneous-reading-and-writing
        // database.beginTransactionNonExclusive();
    }

    @Override
    public void endTransaction() {
        database.endTransaction();
    }

    @Override
    public void setTransactionSuccessful() {
        database.setTransactionSuccessful();
    }

    @Override
    public void execSQL(String sql) throws SQLException {
        try {
            database.execSQL(sql);
        } catch (android.database.SQLException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public void execSQL(String sql, Object[] bindArgs) throws SQLException {
        try {
            database.execSQL(sql, bindArgs);
        } catch (android.database.SQLException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public Cursor rawQuery(String sql, String[] selectionArgs) {
        return new SQLiteCursorWrapper(database.rawQuery(sql, selectionArgs));
    }

    @Override
    public long insert(String table, String nullColumnHack, ContentValues values) {
        return database.insert(table, nullColumnHack,
                AndroidSQLiteHelper.toAndroidContentValues(values));
    }

    @Override
    public long insertOrThrow(String table, String nullColumnHack, ContentValues values) throws SQLException {
        try {
            return database.insertOrThrow(table, nullColumnHack, AndroidSQLiteHelper.toAndroidContentValues(values));
        } catch (android.database.SQLException e) {
            if(e instanceof android.database.sqlite.SQLiteConstraintException)
                throw new SQLException(SQLException.SQLITE_CONSTRAINT,e);
            else
                throw new SQLException(e);
        }
    }

    @Override
    public long insertWithOnConflict(String table, String nullColumnHack,
                                     ContentValues initialValues, int conflictAlgorithm) {
        return database.insertWithOnConflict(table, nullColumnHack,
                AndroidSQLiteHelper.toAndroidContentValues(initialValues), conflictAlgorithm);
    }

    @Override
    public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        return database.update(table,
                AndroidSQLiteHelper.toAndroidContentValues(values), whereClause, whereArgs);
    }

    @Override
    public int delete(String table, String whereClause, String[] whereArgs) {
        return database.delete(table, whereClause, whereArgs);
    }

    @Override
    public void close() {
        database.close();
        Log.v(Log.TAG_DATABASE, "%s: Closed Android sqlite db", this);
    }

    @Override
    public boolean supportEncryption() {
        return false;
    }

    @Override
    public byte[] derivePBKDF2SHA256Key(String password, byte[] salt, int rounds) {
        throw new UnsupportedOperationException("The storage doesn't support encryption");
    }

    @Override
    public String toString() {
        return "AndroidSQLiteStorageEngine{" +
                "database=" + Integer.toHexString(System.identityHashCode(database)) +
                "}";
    }
}

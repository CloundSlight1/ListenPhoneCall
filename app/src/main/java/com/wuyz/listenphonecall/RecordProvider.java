package com.wuyz.listenphonecall;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

import java.sql.SQLException;

/**
 * ClimbRecordProvider Created by Administrator on 2015/3/13.
 */
public class RecordProvider extends ContentProvider {

    public static final String AUTHORITY = "com.com.wuyz.listenphonecall.RecordProvider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/record");

    private static final String TAG = "RecordProvider";

    /**
     * ID
     * <p>Type: Integer (long)</p>
     */
    public static final String KEY_ID = "_id";

    /**
     * contact name
     * <p>Type: Text</p>
     */
    public static final String KEY_NAME = "name";

    /**
     * Phone number
     * <p>Type: Text</p>
     */
    public static final String KEY_PHONE = "phone";

    /**
     * file paht
     * <p>Type: Text</p>
     */
    public static final String KEY_PATH = "path";

    /**
     * CallLog.Calls.INCOMING_TYPE or  CallLog.Calls.OUTGOING_TYPE
     * <p>Type: Integer</p>
     */
    public static final String KEY_TYPE = "type";

    /**
     * duration, in ms
     * <p>Type: Integer (long)</p>
     */
    public static final String KEY_DURATION = "duration";

    /**
     * create time
     * <p>Type: Integer (long)</p>
     */
    public static final String KEY_TIME = "time";

    private static final int ALL = 1;
    private static final int SINGLE = 2;

    private static final UriMatcher sUriMatcher;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, "record", ALL);
        sUriMatcher.addURI(AUTHORITY, "record/#", SINGLE);
    }

    private DatabaseHelper mHelper;

    @Override
    public boolean onCreate() {
        mHelper = new DatabaseHelper(getContext(), DatabaseHelper.DATABASE_NAME, null,
                DatabaseHelper.VERSION);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(DatabaseHelper.TABLE_NAME);
        switch (sUriMatcher.match(uri)) {
            case SINGLE:
                builder.appendWhere(KEY_ID + " = " + uri.getPathSegments().get(1));
                break;
        }
        if (TextUtils.isEmpty(sortOrder))
            sortOrder = KEY_TIME + " desc";

        SQLiteDatabase database = mHelper.getReadableDatabase();
        Cursor cursor = builder.query(database, projection, selection, selectionArgs,
                null, null, sortOrder);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case ALL:
                return "vnd.android.cursor.dir/com.wuyz.listenphonecall.record";
            case SINGLE:
                return "vnd.android.cursor.item/com.wuyz.listenphonecall.record";
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Log2.d(TAG, "insert: values[%s]", values);
        long rowId = mHelper.getWritableDatabase().insert(DatabaseHelper.TABLE_NAME, null, values);
        if (rowId > -1) {
            Uri newUri = ContentUris.withAppendedId(uri, rowId);
            getContext().getContentResolver().notifyChange(newUri, null);
            return newUri;
        }
        try {
            throw new SQLException("Failed to insert row into " + uri);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log2.d(TAG, "delete: uri[%s] selection[%s] selectionArgs[%s]", uri, selection,
                (selectionArgs != null && selectionArgs.length > 0) ? selectionArgs[0] : "");
        int count;
        switch (sUriMatcher.match(uri)) {
            case ALL:
                count = mHelper.getWritableDatabase().delete(
                        DatabaseHelper.TABLE_NAME, selection, selectionArgs);
                Log2.d(TAG, "delete all: count[%d]", count);
                break;
            case SINGLE:
                count = mHelper.getWritableDatabase().delete(
                        DatabaseHelper.TABLE_NAME,
                        KEY_ID + " = " + uri.getPathSegments().get(1) +
                                (TextUtils.isEmpty(selection) ? "" : " and (" + selection + ")"),
                        selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        if (count > 0)
            getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count;
        switch (sUriMatcher.match(uri)) {
            case ALL:
                count = mHelper.getWritableDatabase().update(
                        DatabaseHelper.TABLE_NAME, values, selection, selectionArgs);
                break;
            case SINGLE:
                count = mHelper.getWritableDatabase().update(
                        DatabaseHelper.TABLE_NAME, values,
                        KEY_ID + " = " + uri.getPathSegments().get(1) +
                                (TextUtils.isEmpty(selection) ? "" : " and (" + selection + ")"),
                        selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    private class DatabaseHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "record.db";
        private static final String TABLE_NAME = "record";
        private static final int VERSION = 1;

        public DatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
            super(context, name, factory, version);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(String.format("create table %s (" +
                            "%s integer primary key autoincrement, " +
                            "%s text, " +
                            "%s text, " +
                            "%s text, " +
                            "%s integer, " +
                            "%s integer, " +
                            "%s integer);",
                    TABLE_NAME, KEY_ID, KEY_NAME, KEY_PHONE, KEY_PATH, KEY_TYPE, KEY_DURATION, KEY_TIME));
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("drop table if exists " + TABLE_NAME);
            onCreate(db);
        }
    }
}

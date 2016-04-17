package com.wuyz.listenphonecall;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.ContactsContract;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class Utils {

    private static final String TAG = "Utils";

    public static final long DAY_LENGTH = 24 * 3600000;

    public static String getNameByPhone(Context context, String phone) {
        Log2.d(TAG, "getNameByPhone %s", phone);
        if (phone == null || phone.length() <= 0)
            return null;

        Cursor cursor = null;
        try {
            final ContentResolver contentResolver = context.getContentResolver();
            cursor = contentResolver.query(
                    Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, phone),
                    new String[]{ContactsContract.Contacts.DISPLAY_NAME},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
        return null;
    }

    public static Bitmap getPhotoByPhone(Context context, String phone) {
//		Log2.d(TAG, "getPhotoByPhone %s", phone);
        Uri uri = getContactUriByPhone(context, phone);
        if (uri == null)
            return null;
        try {
            InputStream inputStream = ContactsContract.Contacts.openContactPhotoInputStream(
                    context.getContentResolver(), uri, false);
            if (inputStream != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();
//				Log2.d(TAG, "getPhotoByPhone phone[%s], bitmap[%s]", phone, bitmap);
                return bitmap;
            }
        } catch (IOException e) {
            Log2.e(TAG, "getPhotoByPhone", e);
        }
        return null;
    }

    public static Uri getContactUriByPhone(Context context, String phone) {
//		Log2.d(TAG, "getContactUriByPhone %s", phone);
        if (phone == null || phone.length() <= 0)
            return null;

        Cursor cursor = null;
        try {
            final ContentResolver contentResolver = context.getContentResolver();
            cursor = contentResolver.query(
                    Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, phone),
                    new String[]{ContactsContract.Contacts._ID},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                cursor.close();
                Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id);
//				Log2.d(TAG, "getContactUriByPhone phone[%s], id[%s]", phone, id);
                return uri;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
        return null;
    }

    public static String getReadablePhone(String phone) {
        Log2.d(TAG, "getReadablePhone %s", phone);
        if (phone != null) {
            phone = phone.replace("-", "").replace(" ", "");
            if (phone.length() > 11)
                phone = phone.substring(phone.length() - 11);
        }
        return phone;
    }

    public static String getReadableDuration(long duration) {
        if (duration < 0)
            return "";
        int time = (int) (duration / 1000);
        if (time <= 0)
            time = 1;

        int hour = time / 3600;
        time %= 3600;
        int min = time / 60;
        time %= 60;
        StringBuilder builder = new StringBuilder();
        if (hour > 0)
            builder.append(hour).append('h');
        if (min > 0)
            builder.append(min).append('m');
        builder.append(time).append('s');
        return builder.toString();
    }

    public static String getReadableTime(Context context, long time) {
        long currentTime = System.currentTimeMillis();
        int delta = (int) (currentTime - time) / 1000;

        if (delta < 3600)
            return String.format(context.getString(R.string.minutes_ago), delta / 60);

        if (delta < 3600 * 24)
            return String.format(context.getString(R.string.hours_ago), delta / 3600);

        if (delta < 3600 * 24)
            return String.format(context.getString(R.string.hours_ago), delta / 3600);

        return String.format(context.getString(R.string.days_ago), delta / 3600 / 24);
    }

    public static int deleteRecordByTime(Context context, long time) {
        if (time < 0)
            return 0;
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(RecordProvider.CONTENT_URI,
                    new String[]{RecordProvider.KEY_ID, RecordProvider.KEY_PATH},
                    RecordProvider.KEY_TIME + "<" + time, null, null);
            if (cursor != null) {
                int count = cursor.getCount();
                long[] ids = new long[count];
                String[] paths = new String[count];
                for (int i = 0; cursor.moveToNext(); i++) {
                    ids[i] = cursor.getLong(0);
                    paths[i] = cursor.getString(1);
                }
                cursor.close();

                for (int i = 0; i < count; i++) {
                    contentResolver.delete(ContentUris.withAppendedId(RecordProvider.CONTENT_URI, ids[i]), null, null);
                    File file = new File(paths[i]);
                    if (file.exists() && file.isFile())
                        file.delete();
                }

                if (count > 0) {
                    MediaScannerConnection.scanFile(context, new String[]{new File(Environment.getExternalStorageDirectory(), "Record").getAbsolutePath()},
                            null, null);
                }
                return count;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
        return 0;
    }

    public static boolean deleteRecordById(Context context, long id) {
        if (id < 0)
            return false;
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = null;
        try {
            Uri uri = ContentUris.withAppendedId(RecordProvider.CONTENT_URI, id);
            cursor = contentResolver.query(uri,
                    new String[]{RecordProvider.KEY_PATH},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String path = cursor.getString(0);
                cursor.close();
                contentResolver.delete(uri, null, null);
                File file = new File(path);
                if (file.exists() && file.isFile() && file.delete()) {
                    MediaScannerConnection.scanFile(context, new String[]{new File(Environment.getExternalStorageDirectory(), "Record").getAbsolutePath()},
                            null, null);
                    Log2.d(TAG, "deleteRecordById %d ok", id);
                }
                return true;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
                cursor = null;
            }
        }
        return false;
    }

    public static String getRecordPath() {
        return Environment.getExternalStorageDirectory().getPath() + "/Record";
//        File file = new File("/storage/sdcard1/");
//        if (file.exists()) {
//            return "/storage/sdcard1/Record";
//        } else {
//            return "/storage/sdcard0/Record";
//        }
    }
}

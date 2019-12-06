package com.wuyz.listenphonecall;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.CallLog;
import android.telephony.TelephonyManager;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public class RecordService extends Service {
    private static final String TAG = "RecordService";

    public static final String ACTION_SERVICE_STOPPED = "com.wuyz.listenphonecall.action_service_stopped";

    public static final int TYPE_START_RECORD = 0;
    public static final int TYPE_STOP_RECORD = 1;

    public static final int MSG_START_RECORD = 1;
    public static final int MSG_STOP_RECORD = 2;
    public static final int MSG_MEDIA_SCANNED = 3;

    public static final String KEY_TYPE = "type";
    public static final String KEY_PHONE = "phone";
    public static final String KEY_IN_OUT = "inout";


    private boolean mIsRecording = false;
    private MediaRecorder mMediaRecorder;
    private File mSaveFile;
    private TelephonyManager mTelephoneManager;
    //    private SimpleDateFormat mDateFormat;
    private MyHandler mHandler;
    private final Object mLock = new Object();
    private HandlerThread mThread;
    private String mPhone;
    private int mInOut = CallLog.Calls.INCOMING_TYPE;
    private long mStartTime = 0;
    private Notification mNotification;
    private SharedPreferences preferences;

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (!preferences.getBoolean("enable", true)) {
            stopSelf();
            return;
        }
        Log2.d(TAG, "onCreate");
        mTelephoneManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
//        mDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
        mThread = new HandlerThread(TAG);
        mThread.start();
        mHandler = new MyHandler(this, mThread.getLooper());

        new Thread(new Runnable() {
            @Override
            public void run() {
                File destFile = new File(getDir("bin", MODE_PRIVATE), "daemon");
                String filePath = destFile.getAbsolutePath();
                if (!destFile.exists()) {
                    AssetManager manager = getAssets();
                    try (InputStream inputStream = manager.open(Build.CPU_ABI + "/daemon")) {
                        byte[] buffer = new byte[1024];
                        int n;
                        try (FileOutputStream outputStream = new FileOutputStream(destFile)) {
                            while ((n = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, n);
                            }
                        }
                        inputStream.close();
                        int ret = Runtime.getRuntime().exec("chmod 0755 " + filePath).waitFor();
                        Log2.d(TAG, "chmod return %d", ret);
                    } catch (Exception e) {
                        Log2.e(TAG, e);
                    }
                }
                try {
                    int ret = Runtime.getRuntime().exec(String.format(Locale.getDefault(),
                            "%s -p %s -c %s -t 120", filePath, getPackageName(), RecordService.class.getName())).waitFor();
                    Log2.d(TAG, "return %d", ret);
                } catch (Exception e) {
                    Log2.e(TAG, e);
                }
            }
        }).start();
    }

    private Notification buildNotification() {
        if (mNotification != null)
            return mNotification;
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder = new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_record);
        builder.setContentTitle(getString(R.string.notification_title));
        builder.setContentText(getString(R.string.notification_text));
        builder.setContentIntent(pendingIntent);
        mNotification = builder.build();
        return mNotification;
    }

    private void resetVariables() {
        setRecording(false);
        mSaveFile = null;
        mPhone = null;
        mInOut = CallLog.Calls.INCOMING_TYPE;
        mStartTime = 0;
    }

    public File getFile() {
        synchronized (mLock) {
            return mSaveFile;
        }
    }

    public void setFile(File file) {
        synchronized (mLock) {
            mSaveFile = file;
        }
    }

    public boolean isRecording() {
        synchronized (mLock) {
            return mIsRecording;
        }
    }

    public void setRecording(boolean recording) {
        synchronized (mLock) {
            mIsRecording = recording;
        }
    }

    public String getPhone() {
        synchronized (mLock) {
            return mPhone;
        }
    }

    public void setPhone(String phone) {
        synchronized (mLock) {
            mPhone = phone;
        }
    }

    public int getInOut() {
        synchronized (mLock) {
            return mInOut;
        }
    }

    public void setInOut(int inOut) {
        synchronized (mLock) {
            mInOut = inOut;
        }
    }

    @Override
    public void onDestroy() {
        if (preferences.getBoolean("enable", true)) {
            Log2.d(TAG, "onDestroy");
            synchronized (mLock) {
                mLock.notifyAll();
            }
            stopRecord();
            if (mHandler != null) {
                mHandler.removeCallbacksAndMessages(null);
                mHandler = null;
            }
            if (mThread != null) {
                mThread.quit();
                mThread = null;
            }
            sendBroadcast(new Intent(ACTION_SERVICE_STOPPED));
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log2.d(TAG, "onStartCommand %s", intent);
        if (intent == null)
            return START_NOT_STICKY;
        if (!preferences.getBoolean("enable", true)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        int type = intent.getIntExtra(KEY_TYPE, -1);
        switch (type) {
            case TYPE_START_RECORD:
                if (isRecording() || !PhoneReceiver.sIsRecording)
                    break;
                Log2.d(TAG, "onStartCommand TYPE_START_RECORD");
                synchronized (mLock) {
                    mLock.notifyAll();
                }
                setPhone(intent.getStringExtra("phone"));
                setInOut(intent.getIntExtra(KEY_IN_OUT, CallLog.Calls.INCOMING_TYPE));
                mHandler.removeMessages(MSG_START_RECORD);
                mHandler.sendEmptyMessageDelayed(MSG_START_RECORD, 3000);
                break;
            case TYPE_STOP_RECORD:
                Log2.d(TAG, "onStartCommand TYPE_STOP_RECORD");
                synchronized (mLock) {
                    mLock.notifyAll();
                }
//				mHandler.removeMessages(MSG_STOP_RECORD);
//				mHandler.sendEmptyMessage(MSG_STOP_RECORD);
                break;
        }
        return START_STICKY;
    }

    private boolean startRecord() {
        final boolean isRecording = isRecording();
        final File file = getFile();
        Log2.d(TAG, "startRecord isRecording[%b], file[%s]", isRecording, file);
        if (isRecording || file == null)
            return false;

        mStartTime = System.currentTimeMillis();

        startForeground(1, buildNotification());

        if (mMediaRecorder == null)
            mMediaRecorder = new MediaRecorder();

        mMediaRecorder.reset();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mMediaRecorder.setOutputFile(file.getAbsolutePath());
        mMediaRecorder.setMaxDuration(30 * 60000);
        mMediaRecorder.setMaxFileSize(10 * 1024 * 1024);
        mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                switch (what) {
                    case MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED:
                    case MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED:
                        stopRecord();
                        break;
                    case MediaRecorder.MEDIA_ERROR_SERVER_DIED:
                    case MediaRecorder.MEDIA_RECORDER_ERROR_UNKNOWN:
                        if (mMediaRecorder != null) {
                            mMediaRecorder.stop();
                            mMediaRecorder.release();
                            mMediaRecorder = null;
                        }
                        break;
                }
            }
        });
        try {
            //mMediaRecorder.prepare();
            prepareRecord(mMediaRecorder);
            mMediaRecorder.start();
            setRecording(true);
            PhoneReceiver.sIsRecording = true;
            while (isRecording()) {
                synchronized (mLock) {
                    mLock.wait(20000);
                }
                if (mTelephoneManager.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                    Log2.d(TAG, "startRecord isRecording, try to stop record because call is idle");
                    stopRecord();
                    break;
                }
            }
            return true;
        } catch (Exception e) {
            Log2.e(TAG, "startRecord", e);
            stopRecord();
            return false;
        }
    }

    private void stopRecord() {
        synchronized (mLock) {
            if (mMediaRecorder != null) {
                Log2.d(TAG, "stopRecord");
                stopForeground(true);

                try {
                    mMediaRecorder.stop();
                } catch (IllegalStateException e) {
                    Log2.e(TAG, "stopRecord", e);
                }
                mMediaRecorder.release();
                mMediaRecorder = null;

                File file = getFile();
                if (file != null && file.isFile()) {
                    int duration = (int) (System.currentTimeMillis() - mStartTime);
                    if (duration < 8000) {
                        file.delete();
                        PhoneReceiver.sIsRecording = false;
                        resetVariables();
                    } else {
                        MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
                        mHandler.removeMessages(MSG_MEDIA_SCANNED);
                        mHandler.obtainMessage(MSG_MEDIA_SCANNED, file.getAbsolutePath()).sendToTarget();
                    }
                }
            }
        }
    }

    private static Field sRecordPath;
    private static Field sRecordFd;
    private static Method sRecordSetOutputFile;
    private static Method sRecordPrepare;

    static {
        try {
            sRecordPath = MediaRecorder.class.getDeclaredField("mPath");
            sRecordPath.setAccessible(true);

            sRecordFd = MediaRecorder.class.getDeclaredField("mFd");
            sRecordFd.setAccessible(true);

            sRecordSetOutputFile = MediaRecorder.class.getDeclaredMethod("_setOutputFile",
                    FileDescriptor.class, long.class, long.class);
            sRecordSetOutputFile.setAccessible(true);

            sRecordPrepare = MediaRecorder.class.getDeclaredMethod("_prepare", (Class[]) null);
            sRecordPrepare.setAccessible(true);
        } catch (Exception e) {
            Log2.e(TAG, "", e);
        }
    }

    /*
    * if (mPath != null) {
            RandomAccessFile file = new RandomAccessFile(mPath, "rws");
            try {
                _setOutputFile(file.getFD(), 0, 0);
            } finally {
                file.close();
            }
        } else if (mFd != null) {
            _setOutputFile(mFd, 0, 0);
        } else {
            throw new IOException("No valid output file");
        }

        _prepare();
    */
    public static boolean prepareRecord(MediaRecorder recorder) {
        try {
            recorder.prepare();
            return true;
        } catch (IOException e) {
            Log2.e(TAG, "prepareRecord", e);
            try {
                String path = (String) sRecordPath.get(recorder);
                FileDescriptor fd = (FileDescriptor) sRecordFd.get(recorder);
                if (path != null) {
                    RandomAccessFile file = new RandomAccessFile(path, "rws");
                    try {
                        sRecordSetOutputFile.invoke(recorder, file.getFD(), 0L, 0L);
                    } finally {
                        file.close();
                    }
                } else if (fd != null) {
                    sRecordSetOutputFile.invoke(recorder, fd, 0L, 0L);
                } else {
                    throw new IOException("No valid output file");
                }

                sRecordPrepare.invoke(recorder, (Object[]) null);
                return true;
            } catch (Exception e1) {
                Log2.e(TAG, "prepareRecord", e);
            }
        }
        return false;
    }

    private void doWhenScanComplete(String path) {
//        final ContentResolver contentResolver = getContentResolver();
//        final long time = System.currentTimeMillis();
//        Utils.deleteRecordByTime(this, time - 30 * Utils.DAY_LENGTH);
//
//        ContentValues values = new ContentValues(6);
//        values.put(RecordProvider.KEY_DURATION, time - mStartTime);
//        String phone = getPhone();
//        values.put(RecordProvider.KEY_NAME, Utils.getNameByPhone(RecordService.this, phone));
//        values.put(RecordProvider.KEY_PHONE, phone);
//        values.put(RecordProvider.KEY_PATH, path);
//        values.put(RecordProvider.KEY_TYPE, getInOut());
//        values.put(RecordProvider.KEY_TIME, time);
//        contentResolver.insert(RecordProvider.CONTENT_URI, values);
        PhoneReceiver.sIsRecording = false;

        resetVariables();
    }

    private static class MyHandler extends Handler {
        private WeakReference<Context> mContext;

        public MyHandler(Context context, Looper looper) {
            super(looper);
            mContext = new WeakReference<>(context);
        }

        @Override
        public void handleMessage(final Message msg) {
            RecordService service = (RecordService) mContext.get();
            if (service == null)
                return;
            switch (msg.what) {
                case MSG_START_RECORD:
                    Log2.d(TAG, "handleMessage MSG_START_RECORD");
                    if (service.isRecording() || !PhoneReceiver.sIsRecording ||
                            service.mTelephoneManager.getCallState() == TelephonyManager.CALL_STATE_IDLE)
                        break;
                    File recordPath = new File(Utils.getRecordPath(service));
                    Log2.d(TAG, "%s", recordPath);
                    if (!recordPath.exists()) {
                        if (!recordPath.mkdirs()) {
                            Log2.e(TAG, "handleMessage mkdir failed: " + recordPath);
                            return;
                        }
                    }
                    // lxx_551_0_12215111.amr
                    String phone = service.getPhone();
                    try {
                        File file = new File(recordPath, String.format(Locale.getDefault(), "%s_%s_%d_%d.amr",
                                Utils.getNameByPhone(service, phone), phone, service.getInOut(), System.currentTimeMillis()));
                        if (file.exists())
                            file.delete();
                        service.setFile(file);
                        service.startRecord();
                    } catch (Exception e) {
                        Log2.e(TAG, e);
                    }
                    break;
                case MSG_STOP_RECORD:
                    Log2.d(TAG, "handleMessage MSG_STOP_RECORD");
                    service.stopRecord();
                    break;
                case MSG_MEDIA_SCANNED:
                    Log2.d(TAG, "handleMessage MSG_MEDIA_SCANNED");
                    service.doWhenScanComplete((String) msg.obj);
                    break;
            }
        }
    }
}

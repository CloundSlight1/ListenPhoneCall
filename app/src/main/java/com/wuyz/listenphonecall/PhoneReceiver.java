package com.wuyz.listenphonecall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.CallLog;
import android.telephony.TelephonyManager;

public class PhoneReceiver extends BroadcastReceiver {

    private static final String TAG = "PhoneReceiver";

    public static boolean sIsRecording = false;
    public static String sPhone;
    private static int sType = CallLog.Calls.INCOMING_TYPE;
    private static long sStartTime;

    @Override
    public void onReceive(final Context context, Intent intent) {
        String action = intent.getAction();
        Log2.d(TAG, "onReceive action[%s] isRecording[%b]", action, sIsRecording);
        TelephonyManager telephoneManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        final Intent serviceIntent = new Intent(context, RecordService.class);
        context.startService(serviceIntent);
        switch (action) {
            case TelephonyManager.ACTION_PHONE_STATE_CHANGED:
                int state = telephoneManager.getCallState();
                switch (state) {
                    case TelephonyManager.CALL_STATE_IDLE:
                        Log2.d(TAG, "onReceive CALL_STATE_IDLE");
                        if (SystemClock.elapsedRealtime() - sStartTime > 100L) {
                            serviceIntent.putExtra(RecordService.KEY_TYPE, RecordService.TYPE_STOP_RECORD);
                            context.startService(serviceIntent);
                            sIsRecording = false;
                        } else
                            Log2.w(TAG, "onReceive, delta time less than 100ms, ignore");
                        break;
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (!sIsRecording) {
                            sStartTime = SystemClock.elapsedRealtime();
                            String phone = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
                            if (phone != null)
                                sPhone = phone;
                            sType = CallLog.Calls.INCOMING_TYPE;
                            Log2.d(TAG, "onReceive CALL_STATE_RINGING");
                        }
                        break;
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        Log2.d(TAG, "onReceive CALL_STATE_OFFHOOK sPhone[%s]", sPhone);
                        if (sPhone != null && sPhone.length() > 0) {
                            sStartTime = SystemClock.elapsedRealtime();
                            serviceIntent.putExtra(RecordService.KEY_TYPE, RecordService.TYPE_START_RECORD);
                            serviceIntent.putExtra(RecordService.KEY_PHONE, sPhone);
                            serviceIntent.putExtra(RecordService.KEY_IN_OUT, sType);
                            context.startService(serviceIntent);
                            sIsRecording = true;
                            sPhone = null;
                        }
                        break;
                    default:
                        return;
                }
                break;
            case Intent.ACTION_NEW_OUTGOING_CALL:
                if (!sIsRecording) {
                    sStartTime = SystemClock.elapsedRealtime();
                    sPhone = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
                    sType = CallLog.Calls.OUTGOING_TYPE;
                    Log2.d(TAG, "onReceive ACTION_NEW_OUTGOING_CALL sPhone[%s]", sPhone);
                }
                break;
        }
    }
}

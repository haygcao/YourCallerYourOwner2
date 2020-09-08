package dummydomain.yetanothercallblocker;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dummydomain.yetanothercallblocker.data.YacbHolder;

import static java.util.Objects.requireNonNull;

public class CallMonitoringService extends Service {

    private static final String ACTION_START = "YACB_ACTION_START";
    private static final String ACTION_STOP = "YACB_ACTION_STOP";

    private static final Logger LOG = LoggerFactory.getLogger(CallMonitoringService.class);

    private final MyPhoneStateListener phoneStateListener = new MyPhoneStateListener(this);
    private final CallReceiver callReceiver = new CallReceiver(true);

    private boolean monitoringStarted;

    public static void start(Context context) {
        ContextCompat.startForegroundService(context, getIntent(context, ACTION_START));
    }

    public static void stop(Context context) {
        context.stopService(getIntent(context, ACTION_STOP));
    }

    private static Intent getIntent(Context context, String action) {
        Intent intent = new Intent(context, CallMonitoringService.class);
        intent.setAction(action);
        return intent;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LOG.debug("onStartCommand({})", intent);

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopMonitoring();
            stopForeground();
            stopSelf();
        } else {
            startForeground();
            startMonitoring();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        LOG.debug("onBind({})", intent);
        return null;
    }

    @Override
    public void onDestroy() {
        LOG.debug("onDestroy()");
        stopMonitoring();
    }

    private void startForeground() {
        startForeground(NotificationHelper.NOTIFICATION_ID_MONITORING_SERVICE,
                NotificationHelper.createMonitoringServiceNotification(this));
    }

    private void stopForeground() {
        stopForeground(true);
    }

    private void startMonitoring() {
        if (monitoringStarted) return;
        monitoringStarted = true;

        try {
            getTelephonyManager().listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(TelephonyManager.EXTRA_STATE_RINGING); // TODO: check
            intentFilter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
            registerReceiver(callReceiver, intentFilter);
        } catch (Exception e) {
            LOG.error("startMonitoring()", e);
        }
    }

    private void stopMonitoring() {
        if (!monitoringStarted) return;

        try {
            getTelephonyManager().listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);

            unregisterReceiver(callReceiver);
        } catch (Exception e) {
            LOG.error("stopMonitoring()", e);
        }

        monitoringStarted = false;
    }

    private TelephonyManager getTelephonyManager() {
        return requireNonNull(
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE));
    }

    private static class MyPhoneStateListener extends PhoneStateListener {

        private static final Logger LOG = LoggerFactory.getLogger(MyPhoneStateListener.class);

        private final Context context;

        public MyPhoneStateListener(Context context) {
            this.context = context;
        }

        @Override
        public void onCallStateChanged(int state, String phoneNumber) {
            LOG.info("onCallStateChanged({}, \"{}\")", state, phoneNumber);

            /*
             * According to docs, an empty string may be passed if the app lacks permissions.
             * The app deals with permissions in PhoneStateHandler.
             */
            if (TextUtils.isEmpty(phoneNumber)) {
                phoneNumber = null;
            }

            PhoneStateHandler phoneStateHandler = YacbHolder.getPhoneStateHandler();
            switch (state) {
                case TelephonyManager.CALL_STATE_IDLE:
                    phoneStateHandler.onIdle(context, phoneNumber);
                    break;
                case TelephonyManager.CALL_STATE_RINGING:
                    phoneStateHandler.onRinging(context, phoneNumber);
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    phoneStateHandler.onOffHook(context, phoneNumber);
                    break;
            }
        }
    }

}
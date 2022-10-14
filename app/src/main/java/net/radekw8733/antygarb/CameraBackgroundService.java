package net.radekw8733.antygarb;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;

import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.LifecycleService;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class CameraBackgroundService extends LifecycleService implements KeypointsReturn{
    private static CameraInferenceUtil util;
    public static CameraInferenceUtil.CalibratedPose calibratedPose;
    private NotificationManagerCompat notificationManager;
    private static Timer timer;
    private int notificationID = (int) (Math.random() * 10000);
    private int wrongPostureCounter = 0;
    public static boolean isRunning = false; // static variable to avoid launching multiple services

    public CameraBackgroundService() {}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        calibratedPose = MainActivity.calibratedPose;

        enableBusyNotification();
        addReceiver();

        util = new CameraInferenceUtil(this);
        util.setKeypointCallback(this);
        util.setupCamera();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        stopService();

        super.onDestroy();
    }

    public static void setupTimer() {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                util.takePicture();
            }
        }, 0, 5000);
    }

    private void sendNotification() {
        Intent intent = new Intent("net.radekw8733.Antygarb.ANTYGARB_DISMISS_NOTIFICATION");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(this, getString(R.string.notification_channel))
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.antygarb_notification_icon)
                .setContentIntent(pendingIntent)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();

        notificationManager.notify(notificationID, notification);
    }

    private void removeNotification() {
        notificationManager.cancel(notificationID);
    }

    private void addReceiver() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                removeNotification();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("net.radekw8733.Antygarb.ANTYGARB_DISMISS_NOTIFICATION");
        registerReceiver(receiver, filter);
    }

    public void returnKeypoints(Map<String, CameraInferenceUtil.Keypoint> keypoints) {
        if (keypoints.size() > 0) {
            if (!util.estimatePose(keypoints, calibratedPose)) {
                if (wrongPostureCounter >= 6) {
                    // if wrong posture is through 30s of time, send notification
                    sendNotification();
                    wrongPostureCounter = 0;
                }
                wrongPostureCounter++;
            }
            else {
                // reset counter
                wrongPostureCounter = 0;
                removeNotification();
            }
        }
    }

    private void stopService() {
        isRunning = false;
        timer.cancel();
        stopSelf();
    }

    private void enableBusyNotification() {
        notificationManager = NotificationManagerCompat.from(this);

        BroadcastReceiver stopReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                stopService();
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("net.radekw8733.Antygarb.ANTYGARB_EXIT");
        registerReceiver(stopReceiver, filter);

        Intent backIntent = new Intent("net.radekw8733.Antygarb.ANTYGARB_EXIT");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, backIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification =
                new Notification.Builder(this, getString(R.string.service_notification_channel))
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(getString(R.string.service_notification_text))
                        .setSmallIcon(R.drawable.antygarb_notification_icon)
                        .setColor(Color.rgb(0, 0, 255))
                        .setColorized(true)
                        .setContentIntent(pendingIntent)
                        .build();
        startForeground(1, notification);
    }
}
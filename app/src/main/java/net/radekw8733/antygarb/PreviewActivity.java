package net.radekw8733.antygarb;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.room.Room;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.ShapeDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewOverlay;
import android.widget.TextView;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Map;

public class MainActivity extends AppCompatActivity implements KeypointsReturn {
    private CameraInferenceUtil util;
    public static CameraInferenceUtil.CalibratedPose calibratedPose = new CameraInferenceUtil.CalibratedPose();
    private Map<String, CameraInferenceUtil.Keypoint> lastPose;
    private ViewOverlay overlay;

    private static UsageTimeDatabase usageTimeDatabase;
    public static UsageTimeDao usageTimeDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_camera);

        usageTimeDatabase = Room.databaseBuilder(getApplicationContext(), UsageTimeDatabase.class, "antygarb-usage-time-db").build();
        usageTimeDao = usageTimeDatabase.usageTimeDao();

        overlay = findViewById(R.id.previewView).getOverlay();
        util = new CameraInferenceUtil(this, findViewById(R.id.previewView));
        util.setKeypointCallback(this);
        setupNotificationChannel();
        enableStopIntentFilter();
        requestPermissions();
    }

    private void setupNotificationChannel() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);

        // busy notification channel
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(getString(R.string.service_notification_channel), getString(R.string.service_notification_channel), importance);
        notificationManager.createNotificationChannel(channel);

        // bad posture notification channel
        int notifImportance = NotificationManager.IMPORTANCE_HIGH;
        NotificationChannel notifChannel = new NotificationChannel(getString(R.string.notification_channel), getString(R.string.notification_channel), notifImportance);
        notificationManager.createNotificationChannel(notifChannel);
    }

    private void enableStopIntentFilter() {
        BroadcastReceiver receiver= new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                finishAndRemoveTask();
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("net.radekw8733.Antygarb.ANTYGARB_EXIT");
        registerReceiver(receiver, filter);
    }

    private void requestPermissions() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            util.setupCamera();
        }
        else {
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle(R.string.dialog_title)
                    .setMessage(R.string.dialog_explanation)
                    .setIcon(R.drawable.videocam)
                    .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            requestPermissions(new String[] { Manifest.permission.CAMERA }, 1);
                        }
                    })
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            util.setupCamera();
        }
        else {
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle(R.string.dialog_title)
                    .setMessage(R.string.dialog_problem)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            finish();
                        }
                    })
                    .show();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void onClick(View v) {
        setCalibratedPose();
    }

    private void setCalibratedPose() {
        if (lastPose != null) {
            CameraInferenceUtil.Keypoint leftShoulder = lastPose.get("left_shoulder");
            CameraInferenceUtil.Keypoint rightShoulder = lastPose.get("right_shoulder");

            if (leftShoulder != null && rightShoulder != null) {
                calibratedPose.leftShoulder = lastPose.get("left_shoulder");
                calibratedPose.rightShoulder = lastPose.get("right_shoulder");

                calibratedPose.shoulderLevel = Math.abs(Math.round(leftShoulder.y * 100) - Math.round(rightShoulder.y * 100));

                calibratedPose.isCalibrated = true;
            }
        }
    }

    public void returnKeypoints(Map<String, CameraInferenceUtil.Keypoint> keypoints) {
        lastPose = keypoints;
        PreviewView previewView = findViewById(R.id.previewView);
        int width = previewView.getWidth();
        int height = previewView.getHeight();
        for (CameraInferenceUtil.Keypoint point : keypoints.values()) {
            if (point.confidence > 30) {
                ShapeDrawable drawable = util.newPoint(Math.round(point.x * width), Math.round(point.y * height));
                overlay.add(drawable);
            }
        }

        TextView text = findViewById(R.id.statusText);
        if (!util.estimatePose(keypoints, calibratedPose)) {
            text.setVisibility(View.VISIBLE);
        }
        else {
            text.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    protected void onPause() {
        startService();
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (CameraBackgroundService.calibratedPose != null) {
            calibratedPose = CameraBackgroundService.calibratedPose;
        }
        stopService();
        super.onResume();
    }

    private void startService() {
        if (CameraBackgroundService.isRunning) {
            stopService();
        }

        if (calibratedPose.isCalibrated) {
            Intent intent = new Intent(this, CameraBackgroundService.class);
            startForegroundService(intent);
        }
    }

    private void stopService() {
        Intent intent = new Intent(this, CameraBackgroundService.class);
        intent.setClass(this, CameraBackgroundService.class);
        stopService(intent);
    }
}
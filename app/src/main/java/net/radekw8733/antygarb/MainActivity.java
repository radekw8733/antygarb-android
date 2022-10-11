package net.radekw8733.antygarb;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
import android.view.ViewOverlay;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Map;

public class MainActivity extends AppCompatActivity implements KeypointsReturn {
    private CameraInferenceUtil util;
    private ViewOverlay overlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_camera);
        overlay = findViewById(R.id.previewView).getOverlay();

        util = new CameraInferenceUtil(this, findViewById(R.id.previewView));
        util.setKeypointCallback(this);
        setupNotificationChannel();
        enableStopIntentFilter();
        requestPermissions();
    }

    private void setupNotificationChannel() {
        CharSequence name = getString(R.string.notification_channel);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(getString(R.string.notification_channel), name, importance);
        channel.setDescription(getString(R.string.notification_text));
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    private void enableStopIntentFilter() {
        BroadcastReceiver receiver= new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                stopService();
                finishAndRemoveTask();
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("net.radekw8733.Antygarb.ANTYGARB_EXIT");
        registerReceiver(receiver, filter);
    }

    private void requestPermissions() {
        if (getApplicationContext().checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            util.setupCamera();
        }
        else {
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle(R.string.dialog_title)
                    .setMessage(R.string.dialog_explanation)
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

    public void returnKeypoints(Map<String, CameraInferenceUtil.Keypoint> keypoints) {
        for (CameraInferenceUtil.Keypoint point : keypoints.values()) {
            if (point.confidence > 30) {
                ShapeDrawable drawable = util.newPoint(point.x, point.y);
                overlay.add(drawable);
            }
        }
    }

    @Override
    protected void onPause() {
        startService();
        super.onPause();
    }

    private void startService() {
        if (!CameraBackgroundService.isRunning) {
            stopService();
        }

        Intent intent = new Intent(this, CameraBackgroundService.class);
        getApplicationContext().startForegroundService(intent);
    }

    private void stopService() {
        Intent intent = new Intent(this, CameraBackgroundService.class);
        intent.setAction("net.radekw8733.Antygarb.ANTYGARB_SERVICE_EXIT");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
}
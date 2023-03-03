package net.radekw8733.antygarb;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.room.Room;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkRequest;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.view.ViewOverlay;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import xyz.schwaab.avvylib.AvatarView;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PreviewActivity extends AppCompatActivity implements KeypointsReturn {
    public static String webserverUrl = "http://192.168.1.2:8100/v1";
    private CameraInferenceUtil util;
    public static CameraInferenceUtil.CalibratedPose calibratedPose = new CameraInferenceUtil.CalibratedPose();
    public static UsageTimeDatabase usageTimeDatabase;
    private UsageTimeDao dao;
    private UsageTimeEntry entry = new UsageTimeEntry();
    private Map<String, CameraInferenceUtil.Keypoint> lastPose;
    private ViewOverlay overlay;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_camera);
        overlay = findViewById(R.id.previewView).getOverlay();

        usageTimeDatabase = Room.databaseBuilder(getApplicationContext(), UsageTimeDatabase.class, "UsageTimeDatabase").build();
        dao = usageTimeDatabase.usageTimeDao();

        entry.id = Math.abs(new Random().nextLong());
        entry.appStarted = LocalDateTime.now();
        entry.type = UsageTimeEntry.Type.APP;

        ((AvatarView) findViewById(R.id.profilePicture)).setImageResource(R.drawable.avatar);

        util = new CameraInferenceUtil(this, findViewById(R.id.previewView));
        util.setKeypointCallback(this);

        prefs = getSharedPreferences("Keys", MODE_PRIVATE);

        firstLaunch();
        setupNotificationChannel();
        enableStopIntentFilter();
        requestCameraAccess();
    }

    private void firstLaunch() {
        if (!prefs.getBoolean("setup_done", false)) {
//            prefs.edit().putLong("uid", Math.abs(new Random().nextLong())).apply();

            requestUserAuth();

            WorkRequest workRequest = new PeriodicWorkRequest.Builder(StatisticsUploadWorker.class,
                    1, TimeUnit.DAYS,
                    1, TimeUnit.HOURS)
                    .setInitialDelay(Duration.ofMinutes(1))
                    .build();
        }
    }

    private void requestUserAuth() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(webserverUrl + "/new-user").get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("Antygarb_Auth", e.toString());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try {
                    JSONObject json = new JSONObject(response.body().string());
                    prefs.edit()
                            .putLong("client_uid", json.getLong("client_uid"))
                            .putString("client_token", json.getString("client_token"))
                            .putBoolean("setup_done", true)
                            .apply();
                } catch (JSONException e) {
                    Log.e("Antygarb_Auth", e.toString());
                }
            }
        });
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

    private void requestCameraAccess() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    util.setupCamera();
                }
                else {
                    promptForPermissions();
                }
            }
            else {
                util.setupCamera();
            }
        }
        else {
            promptForPermissions();
        }
    }

    private void promptForPermissions() {
        new MaterialAlertDialogBuilder(PreviewActivity.this)
                .setTitle(R.string.dialog_title)
                .setMessage(R.string.dialog_explanation)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        List<String> permissions = new ArrayList<String>();
                        permissions.add(Manifest.permission.CAMERA);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
                        }
                        String[] permissionsArray = new String[2];
                        permissions.toArray(permissionsArray);
                        requestPermissions(permissionsArray, 1);
                    }
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // lets assume that permissions order is the same as in request, probably point of bug
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && permissions[0].equals(Manifest.permission.CAMERA)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (grantResults[1] == PackageManager.PERMISSION_GRANTED && permissions[1].equals(Manifest.permission.POST_NOTIFICATIONS)) {
                    util.setupCamera();
                    return;
                }
                else {
                    failedPermissionPrompt();
                    return;
                }
            }
            util.setupCamera();
        }
        else {
            failedPermissionPrompt();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void failedPermissionPrompt() {
        new MaterialAlertDialogBuilder(PreviewActivity.this)
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
        MaterialCardView card = findViewById(R.id.card);
        if (!util.estimatePose(keypoints, calibratedPose)) {
            text.setVisibility(View.VISIBLE);
        }
        else {
            text.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStop() {
        entry.appStopped = LocalDateTime.now();
        new Thread(() -> {
            dao.insert(entry);
        }).start();
        startService();
        super.onStop();
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
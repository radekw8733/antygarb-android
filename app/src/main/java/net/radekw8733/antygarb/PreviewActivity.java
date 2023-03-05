package net.radekw8733.antygarb;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.room.Room;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ListenableWorker;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.WorkerParameters;

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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.RequestBody;
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
            requestUserAuth();

            PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(StatisticsUploadWorker.class,
                    1, TimeUnit.DAYS,
                    1, TimeUnit.HOURS)
                    .build();
            WorkManager workManager = WorkManager.getInstance(this);
//            workManager.cancelAllWork();     for removing older workers
//            workManager.pruneWork();
            workManager.enqueueUniquePeriodicWork("StatWorker", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, workRequest);
        }
    }

    private void requestUserAuth() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(webserverUrl + "/new-apiuser").get().build();
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

    private void submitAppUsage() {
        new Thread(() -> {
            OkHttpClient client = new OkHttpClient();
            Log.i("Antygarb_StatWorker", "Worker start");
            long clientUID = prefs.getLong("client_uid", 0);
            String clientToken = prefs.getString("client_token", "");

            dao = PreviewActivity.usageTimeDatabase.usageTimeDao();
            List<UsageTimeEntry> entries = dao.getAllEntries();
            Log.i("Antygarb_StatWorker", "Usage time retrieved");

            try {
                JSONArray entriesJson = new JSONArray();
                LocalDateTime last_time_uploaded = LocalDateTime.parse(prefs.getString("last_appusage_upload_time", "1970-01-01T00:00:00"));

                for (UsageTimeEntry entry : entries) {
                    if (last_time_uploaded.isBefore(entry.appStarted)) {
                        JSONObject object = new JSONObject();
                        object.put("type", entry.type.toString());
                        object.put("app_started", entry.appStarted);
                        object.put("app_stopped", entry.appStopped);
                        entriesJson.put(object);
                    }
                }
                if (entriesJson.length() != 0) {
                    JSONObject jsonUpload = new JSONObject()
                            .put("client_uid", clientUID)
                            .put("client_token", clientToken)
                            .put("entries", entriesJson);

                    RequestBody requestBody = RequestBody.create(
                            jsonUpload.toString(),
                            MediaType.parse("application/json; charset=utf-8")
                    );
                    Request request = new Request.Builder()
                            .url(PreviewActivity.webserverUrl + "/upload-usage")
                            .post(requestBody)
                            .build();
                    Response response = client.newCall(request).execute();
                    if (response.code() == 200) {
                        prefs.edit().putString("last_appusage_upload_time", LocalDateTime.now().toString()).apply();
                    } else {
                        Log.e("Antygarb_Auth", response.toString());
                    }
                }
            } catch (JSONException e) {
                Log.e("Antygarb_Auth", e.getLocalizedMessage());
            } catch (IOException e) {
                Log.e("Antygarb_Auth", e.getLocalizedMessage());
            }
        }).start();
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

    public void profileOnClick(View v) {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
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
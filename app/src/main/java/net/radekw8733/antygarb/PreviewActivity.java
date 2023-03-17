package net.radekw8733.antygarb;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.room.Room;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ShapeDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOverlay;
import android.view.animation.AlphaAnimation;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textview.MaterialTextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    public static String webserverUrl = "https://api.srv45036.seohost.com.pl/api/v1";
    private CameraInferenceUtil util;
    public static CameraInferenceUtil.CalibratedPose calibratedPose = new CameraInferenceUtil.CalibratedPose();
    public static UsageTimeDatabase usageTimeDatabase;
    private UsageTimeDao dao;
    private UsageTimeEntry entry = new UsageTimeEntry();
    private Map<String, CameraInferenceUtil.Keypoint> lastPose;
    private ViewOverlay overlay;
    private SharedPreferences prefs;
    private long correctTotalPoses = 0;
    private long wrongTotalPoses = 0;
    private int lastWrongPosesTrigger = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_camera);
        overlay = findViewById(R.id.previewView).getOverlay();
        AntygarbServerConnector.setup(getApplicationContext());

        usageTimeDatabase = Room.databaseBuilder(getApplicationContext(), UsageTimeDatabase.class, "UsageTimeDatabase").build();
        dao = usageTimeDatabase.usageTimeDao();

        entry.appStarted = LocalDateTime.now();
        entry.type = UsageTimeEntry.Type.APP;

        util = new CameraInferenceUtil(this, findViewById(R.id.previewView));
        util.setKeypointCallback(this);

        prefs = getSharedPreferences("Keys", MODE_PRIVATE);

        setupProfile();
        setupNotificationChannel();
        enableStopIntentFilter();
        requestCameraAccess();
    }

    private void setupProfile() {
        ((AvatarView) findViewById(R.id.profilePicture)).setImageResource(R.drawable.avatar);
        if (!prefs.getBoolean("setup_done", false)) {
            requestUserAuth();

            PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(StatisticsUploadWorker.class,
                    15, TimeUnit.MINUTES,
                    0, TimeUnit.MINUTES)
                    .build();
            WorkManager workManager = WorkManager.getInstance(this);
//            workManager.cancelAllWork();     for removing older workers
//            workManager.pruneWork();
            workManager.enqueueUniquePeriodicWork("StatWorker", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, workRequest);
        }
        else {
            OkHttpClient client = new OkHttpClient();
            try {
                JSONObject jsonPayload = new JSONObject()
                        .put("client_uid", prefs.getLong("client_uid", 0))
                        .put("client_token", prefs.getString("client_token", ""));
                RequestBody requestBody = RequestBody.create(jsonPayload.toString(), MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder().post(requestBody).url(webserverUrl + "/account-details").build();
                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        // set profile picture
                    }
                });
            }
            catch (JSONException e) {
                e.printStackTrace();
            }
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
//        Intent intent = new Intent(this, LoginActivity.class);
//        startActivity(intent);

        LayoutInflater layoutInflater = getLayoutInflater();
        View dialogRoot = layoutInflater.inflate(R.layout.profile_dialog, null);
        AvatarView avatarView = dialogRoot.findViewById(R.id.profile_dialog_avatar);
        MaterialTextView firstLastName = dialogRoot.findViewById(R.id.profile_dialog_name);
        MaterialTextView email = dialogRoot.findViewById(R.id.profile_dialog_email);

        if (!prefs.getBoolean("account_logged", false)) {
            avatarView.setImageResource(R.drawable.avatar);

            new AlertDialog.Builder(this)
                    .setIcon(R.drawable.avatar)
                    .setTitle(R.string.profile_dialog_account)
                    .setView(dialogRoot)
                    .setPositiveButton(R.string.profile_dialog_loginbutton, (dialog, which) -> {
                        Intent intent = new Intent(this, LoginActivity.class);
                        startActivity(intent);
                    })
                    .setNegativeButton(R.string.profile_dialog_registerbutton, (dialog, which) -> {
                        Intent intent = new Intent(this, RegisterActivity.class);
                        startActivity(intent);
                    })
                    .setNeutralButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                    .setCancelable(true)
                    .create().show();
        }
        else {
            avatarView.setBorderColor(R.color.colorPrimary);
            avatarView.setBorderColorEnd(R.color.colorSecondary);
            avatarView.setBorderThickness(10);

            firstLastName.setText(prefs.getString("account_first_name", "") + " " + prefs.getString("account_last_name", ""));
            email.setText(prefs.getString("account_email", ""));

            new AlertDialog.Builder(this)
                    .setIcon(R.drawable.avatar)
                    .setTitle(R.string.profile_dialog_account)
                    .setView(dialogRoot)
                    .setNeutralButton(android.R.string.cancel, (dialog, which) -> dialog.cancel())
                    .setCancelable(true)
                    .create().show();
        }
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

        if (!util.estimatePose(keypoints, calibratedPose)) {
            if (lastWrongPosesTrigger < 5) {
                lastWrongPosesTrigger++;
            }
            wrongTotalPoses++;
        }
        else {
            if (lastWrongPosesTrigger > 0) {
                lastWrongPosesTrigger--;
            }
            correctTotalPoses++;
        }

        TextView text = findViewById(R.id.statusText);
        if (lastWrongPosesTrigger == 5) {
            text.setVisibility(View.VISIBLE);
        }
        if (lastWrongPosesTrigger == 0) {
            text.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStop() {
        entry.appStopped = LocalDateTime.now();
        entry.correctPoses = correctTotalPoses;
        entry.incorrectPoses = wrongTotalPoses;
        entry.correctToIncorrectPoseNormalised = (float) correctTotalPoses + 1 / (wrongTotalPoses + 1);
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
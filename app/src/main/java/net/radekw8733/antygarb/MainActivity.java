package net.radekw8733.antygarb;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
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
import android.os.Bundle;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;

public class MainActivity extends AppCompatActivity {
    private PreviewView cameraPreview;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_camera);

        cameraPreview = findViewById(R.id.previewView);

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
            setupCamera();
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
            setupCamera();
        }
        else {
            new MaterialAlertDialogBuilder(MainActivity.this)
                    .setTitle(R.string.dialog_title)
                    .setMessage(R.string.dialog_problem)
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

    private void setupCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                setupPreview(cameraProvider);
            }
            catch (Exception ignored) {}
        }, ContextCompat.getMainExecutor(this));
    }

    private void setupPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();

        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());

        camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview);
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
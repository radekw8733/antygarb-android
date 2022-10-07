package net.radekw8733.antygarb;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import net.radekw8733.antygarb.ml.LiteModelMovenetSingleposeLightningTfliteInt84;

import java.io.IOException;

public class CameraBackgroundService extends Service {

    private LiteModelMovenetSingleposeLightningTfliteInt84 model;
    ProcessCameraProvider cameraProvider;
    private Camera camera;

    public static boolean isRunning = false; // static variable to avoid launching multiple services

    public CameraBackgroundService() {}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        isRunning = true;
        registerStopBroadcastReceiver();
        enableBusyNotification();
        enableStopIntentFilter();
        loadMovenetModel();
        setupCamera();

        return super.onStartCommand(intent, flags, startId);
    }

    private void registerStopBroadcastReceiver() {
        BroadcastReceiver stopBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                stopService();
            }
        };
        IntentFilter stopFilter = new IntentFilter();
        stopFilter.addAction("net.radekw8733.Antygarb.ANTYGARB_EXIT");
        registerReceiver(stopBroadcastReceiver, stopFilter);
    }

    private void stopService() {
        model.close();
        stopForeground(STOP_FOREGROUND_REMOVE);
        isRunning = false;
    }

    private void enableBusyNotification() {
        Intent backIntent = new Intent("net.radekw8733.Antygarb.ANTYGARB_EXIT");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, backIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification =
                new Notification.Builder(this, getString(R.string.notification_channel))
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(getString(R.string.notification_text))
                        .setSmallIcon(R.drawable.antygarb_notification_icon)
                        .setContentIntent(pendingIntent)
                        .build();
        startForeground(1, notification);
    }

    private void enableStopIntentFilter() {
        BroadcastReceiver receiver= new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                stopService();
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("net.radekw8733.Antygarb.ANTYGARB_SERVICE_EXIT");
        registerReceiver(receiver, filter);
    }

    private void loadMovenetModel() {
        try {
            model = LiteModelMovenetSingleposeLightningTfliteInt84.newInstance(this);
        }
        catch (IOException e) {
            model.close();
            Log.e(getString(R.string.app_name) + " TF", e.toString());
        }
    }

    private void setupCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                setupLifecycle(cameraProvider);
            }
            catch (Exception ignored) {}
        }, ContextCompat.getMainExecutor(this));
    }

    private void setupLifecycle(ProcessCameraProvider cameraProvider) {
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();

        camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
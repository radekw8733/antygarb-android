package net.radekw8733.antygarb;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

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

import net.radekw8733.antygarb.ml.LiteModelMovenetSingleposeLightning3;

public class CameraActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_camera);
        setupNotificationChannel();
        enableStopIntentFilter();

        requestPermissions();
//            Intent intent = new Intent(this, CameraBackgroundService.class);
//            getApplicationContext().startForegroundService(intent);
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
                finishAndRemoveTask();
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("antygarb_exit");
        registerReceiver(receiver, filter);
    }

    private void requestPermissions() {
        if (getApplicationContext().checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            enableService();
        }
        else {
            new MaterialAlertDialogBuilder(CameraActivity.this)
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
            enableService();
        }
        else {
            new MaterialAlertDialogBuilder(CameraActivity.this)
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

    private void enableService() {

    }
}
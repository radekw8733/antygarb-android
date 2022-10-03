package net.radekw8733.antygarb;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import net.radekw8733.antygarb.ml.LiteModelMovenetSingleposeLightning3;

import java.io.IOException;

public class CameraBackgroundService extends Service {

    private LiteModelMovenetSingleposeLightning3 model;

    public CameraBackgroundService() {}

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent backIntent = new Intent("antygarb_exit");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, backIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification =
                new Notification.Builder(this, getString(R.string.notification_channel))
                        .setContentTitle(getString(R.string.app_name))
                        .setContentText(getString(R.string.notification_text))
                        .setSmallIcon(R.drawable.antygarb_notification_icon)
                        .setContentIntent(pendingIntent)
                        .build();
        startForeground(1, notification);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction("antygarb_exit");
        registerReceiver(receiver, filter);

        return super.onStartCommand(intent, flags, startId);
    }

    private void loadMovenetModel() {
        try {
            model = LiteModelMovenetSingleposeLightning3.newInstance(this);
        }
        catch (IOException e) {
            model.close();
            Log.e(getString(R.string.app_name) + " TF", e.toString());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
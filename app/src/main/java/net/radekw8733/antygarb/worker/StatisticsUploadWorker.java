package net.radekw8733.antygarb.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import net.radekw8733.antygarb.onlineio.AntygarbServerConnector;

public class StatisticsUploadWorker extends Worker {
    public StatisticsUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        return AntygarbServerConnector.uploadStatistics() ? Result.success() : Result.failure();
    }
}

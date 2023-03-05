package net.radekw8733.antygarb;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ResourceBundle;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class StatisticsUploadWorker extends Worker {
    private SharedPreferences prefs = getApplicationContext().getSharedPreferences("Keys", Context.MODE_PRIVATE);
    private OkHttpClient client;
    private UsageTimeDao dao;
    public StatisticsUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
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

        return Result.failure();
    }
}

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
        long clientUID = prefs.getLong("client_uid", 0);
        String clientToken = prefs.getString("client_token", "");

        dao = PreviewActivity.usageTimeDatabase.usageTimeDao();
        List<UsageTimeEntry> entries = dao.getAllEntries();

        try {
            JSONArray entriesJson = new JSONArray(entries);
            JSONObject jsonUpload = new JSONObject()
                    .put("client_uid", clientUID)
                    .put("client_token", clientToken);

            RequestBody requestBody = RequestBody.create(
                    jsonUpload.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );
            Request request = new Request.Builder()
                    .url(PreviewActivity.webserverUrl)
                    .post(requestBody)
                    .build();
            Response response = client.newCall(request).execute();
            JSONObject responseJson = new JSONObject(response.toString());
            if (responseJson.getString("status").equals("ok")) {
                return Result.success();
            }
        } catch (JSONException e) {
            Log.e("Antygarb_Auth", e.getLocalizedMessage());
        } catch (IOException e) {
            Log.e("Antygarb_Auth", e.getLocalizedMessage());
        }

        return Result.failure();
    }
}

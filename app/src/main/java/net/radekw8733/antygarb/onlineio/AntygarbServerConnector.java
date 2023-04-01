package net.radekw8733.antygarb.onlineio;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import net.radekw8733.antygarb.db.UsageTimeDao;
import net.radekw8733.antygarb.db.UsageTimeEntry;
import net.radekw8733.antygarb.activity.PreviewActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AntygarbServerConnector {
    private static Context context;
    private static UsageTimeDao dao;
    private static OkHttpClient client;
//    public static String webserverUrl = "http://192.168.1.2:8100/api/v1";
    public static String webserverUrl = "https://api.srv45036.seohost.com.pl/api/v1";
    private static SharedPreferences prefs;

//    private AntygarbServerConnector() {};
//    private static class AntygarbServerConnectorHolder {
//        public static final AntygarbServerConnector instance = new AntygarbServerConnector();
//    }
//    public static AntygarbServerConnector getInstance() {
//        return AntygarbServerConnectorHolder.instance;
//    }

    public static void setup(Context context){
        AntygarbServerConnector.context = context;
        prefs = context.getSharedPreferences("Keys", Context.MODE_PRIVATE);
        client = new OkHttpClient();
    }

    public static void requestUserAuth() {
        Request request = new Request.Builder().url(webserverUrl + "/new-apiuser").get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                AntygarbServerConnector.printError(e);
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
                    AntygarbServerConnector.printError(e);
                }
            }
        });
    }

    public static void loginAccount(AccountStruct account, Callback callback) {
        try {
            JSONObject jsonPayload = new JSONObject()
                    .put("client_uid", account.client_uid)
                    .put("client_token", account.client_token)
                    .put("email", account.email)
                    .put("password", account.password);
            client.newCall(
                    new Request.Builder()
                            .post(RequestBody.create(jsonPayload.toString(), MediaType.get("application/json; charset=utf-8")))
                            .url(webserverUrl + "/login")
                            .build()).enqueue(callback);
        }
        catch (JSONException e) {
            AntygarbServerConnector.printError(e);
        }
    }

    public static void registerAccount(AccountStruct account, Callback callback) {
        try {
            JSONObject jsonPayload = new JSONObject()
                    .put("client_uid", account.client_uid)
                    .put("client_token", account.client_token)
                    .put("email", account.email)
                    .put("password", account.password)
                    .put("first_name", account.first_name)
                    .put("last_name", account.last_name);
            client.newCall(
                    new Request.Builder()
                            .post(RequestBody.create(jsonPayload.toString(), MediaType.get("application/json; charset=utf-8")))
                            .url(AntygarbServerConnector.webserverUrl + "/create-account")
                            .build()).enqueue(callback);
        }
        catch (JSONException e) {
            AntygarbServerConnector.printError(e);
        }
    }

    public static void logout() {
        prefs.edit()
                .putBoolean("account_logged", false)
                .putString("account_first_name", "")
                .putString("account_last_name", "")
                .putString("account_email", "")
                .putString("account_password", "")
                .apply();
        try {
            JSONObject jsonPayload = new JSONObject()
                    .put("client_uid", prefs.getLong("client_uid", 0))
                    .put("client_token", prefs.getString("client_token", ""));
            client.newCall(
                    new Request.Builder()
                            .post(RequestBody.create(jsonPayload.toString(), MediaType.get("application/json; charset=utf-8")))
                            .url(AntygarbServerConnector.webserverUrl + "/logout")
                            .build()).enqueue(new Callback() {
                                @Override
                                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                    AntygarbServerConnector.printError(e);
                                }

                                @Override
                                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                                }
            });
        }
        catch (JSONException e) {
            AntygarbServerConnector.printError(e);
        }
    }

    public static boolean uploadStatistics() {
        if (prefs.getBoolean("setup_done", false)) {
            Log.i("Antygarb_StatWorker", "Worker start");
            UserStruct user = new UserStruct();
            user.client_uid = prefs.getLong("client_uid", 0);
            user.client_token = prefs.getString("client_token", "");

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
                        object.put("correct_poses", entry.correctPoses);
                        object.put("incorrect_poses", entry.incorrectPoses);
                        object.put("correct_to_incorrect", entry.correctToIncorrectPoseNormalised);
                        entriesJson.put(object);
                    }
                }
                if (entriesJson.length() != 0) {
                    JSONObject jsonUpload = new JSONObject()
                            .put("client_uid", user.client_uid)
                            .put("client_token", user.client_token)
                            .put("entries", entriesJson);

                    RequestBody requestBody = RequestBody.create(
                            jsonUpload.toString(),
                            MediaType.parse("application/json; charset=utf-8")
                    );
                    Request request = new Request.Builder()
                            .url(webserverUrl + "/upload-usage")
                            .post(requestBody)
                            .build();
                    Response response = client.newCall(request).execute();
                    if (response.code() == 200) {
                        prefs.edit().putString("last_appusage_upload_time", LocalDateTime.now().toString()).apply();
                    } else {
                        AntygarbServerConnector.printError(response.toString());
                    }
                }
            } catch (JSONException | IOException e) {
                AntygarbServerConnector.printError(e);
            }
            return false;
        }
        return false;
    }

    public static void getAccountDetails(Callback callback) {
        try {
            UserStruct user = new UserStruct();
            user.client_uid = prefs.getLong("client_uid", 0);
            user.client_token = prefs.getString("client_token", "");
            JSONObject jsonPayload = new JSONObject()
                    .put("client_uid", user.client_uid)
                    .put("client_token", user.client_token);
            RequestBody requestBody = RequestBody.create(jsonPayload.toString(), MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder().post(requestBody).url(webserverUrl + "/account-details").build();
            client.newCall(request).enqueue(callback);
        }
        catch (JSONException e) {
            AntygarbServerConnector.printError(e);
        }
    }

    private static void printError(Exception e) {
        Log.e("Antygarb_API", e.getLocalizedMessage());
    }

    private static void printError(String e) {
        Log.e("Antygarb_API", e);
    }

    public static void uploadStatisticsThreaded() {
        new Thread(() -> {
            uploadStatistics();
        }).start();
    }
}

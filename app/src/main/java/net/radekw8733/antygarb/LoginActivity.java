package net.radekw8733.antygarb;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec;
import com.google.android.material.progressindicator.IndeterminateDrawable;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_login);
        prefs = getSharedPreferences("Keys", MODE_PRIVATE);
    }

    public void onLoginButtonClick(View v) {
        ProgressBar progressBar = findViewById(R.id.loginactivity_progressBar);
        progressBar.setVisibility(View.VISIBLE);
        TextInputLayout emailLayout = findViewById(R.id.loginactivity_email_inputlayout);
        TextInputLayout passwordLayout = findViewById(R.id.loginactivity_password_inputlayout);
        TextInputEditText emailInput = findViewById(R.id.loginactivity_email_input);
        TextInputEditText passwordInput = findViewById(R.id.loginactivity_password_input);
        emailLayout.setErrorEnabled(false);
        passwordLayout.setErrorEnabled(false);

        try {
            OkHttpClient client = new OkHttpClient();
            JSONObject jsonPayload = new JSONObject()
                    .put("client_uid", prefs.getLong("client_uid", 0))
                    .put("client_token", prefs.getString("client_token", ""))
                    .put("email", emailInput.getText().toString().trim())
                    .put("password", passwordInput.getText().toString().trim());
            client.newCall(
                    new Request.Builder()
                            .post(RequestBody.create(jsonPayload.toString(), MediaType.get("application/json; charset=utf-8")))
                            .url(PreviewActivity.webserverUrl + "/login")
                            .build()).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Snackbar.make(v, e.toString(), 2000).show();
                    runOnUiThread(() -> progressBar.setVisibility(View.INVISIBLE));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.code() == 200) {
                        try {
                            JSONObject json = new JSONObject(response.body().string());
                            prefs.edit()
                                    .putString("account_first_name", json.getString("first_name"))
                                    .putString("account_last_name", json.getString("last_name"))
                                    .putString("account_email", json.getString("email"))
                                    .putBoolean("account_logged", true).apply();
                            finishActivity(0);
                        } catch (JSONException e) {
                            Snackbar.make(v, e.toString(), 2000).show();
                            runOnUiThread(() -> progressBar.setVisibility(View.INVISIBLE));
                        }
                    }
                    else if (response.code() == 401) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.INVISIBLE);
                            emailLayout.setError(getString(R.string.loginactivity_error));
                            emailLayout.setErrorEnabled(true);
                            passwordLayout.setError(getString(R.string.loginactivity_error));
                            passwordLayout.setErrorEnabled(true);
                        });
                    }
                }
            });
        }
        catch (JSONException e) {
            Snackbar.make(v, e.toString(), 2000).show();
        }
    }
}
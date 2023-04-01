package net.radekw8733.antygarb.activity;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import net.radekw8733.antygarb.onlineio.AccountStruct;
import net.radekw8733.antygarb.onlineio.AntygarbServerConnector;
import net.radekw8733.antygarb.R;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class RegisterActivity extends AppCompatActivity {
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivityIfAvailable(this);
        setContentView(R.layout.activity_register);
        prefs = getSharedPreferences("Keys", MODE_PRIVATE);
    }

    public void onRegisterButtonClick(View v) {
        ProgressBar progressBar = findViewById(R.id.registeractivity_progressBar);
        progressBar.setVisibility(View.VISIBLE);
        TextInputLayout emailLayout = findViewById(R.id.registeractivity_email_inputlayout);
        TextInputLayout passwordLayout = findViewById(R.id.registeractivity_password_inputlayout);
        TextInputEditText emailInput = findViewById(R.id.registeractivity_email_input);
        TextInputEditText passwordInput = findViewById(R.id.registeractivity_password_input);
        TextInputEditText firstNameInput = findViewById(R.id.registeractivity_first_name_input);
        TextInputEditText lastNameInput = findViewById(R.id.registeractivity_last_name_input);
        emailLayout.setErrorEnabled(false);
        passwordLayout.setErrorEnabled(false);

        AccountStruct account = new AccountStruct();
        account.client_uid = prefs.getLong("client_uid", 0);
        account.client_token = prefs.getString("client_token", "");
        account.email = emailInput.getText().toString().trim();
        account.password = passwordInput.getText().toString().trim();
        account.first_name = firstNameInput.getText().toString().trim();
        account.last_name = lastNameInput.getText().toString().trim();

        AntygarbServerConnector.registerAccount(account, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Snackbar.make(v, e.toString(), 2000).show();
                runOnUiThread(() -> progressBar.setVisibility(View.INVISIBLE));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.code() == 200) {
                    prefs.edit()
                            .putString("account_first_name", account.first_name)
                            .putString("account_last_name", account.last_name)
                            .putString("account_email", account.email)
                            .putBoolean("account_logged", true).apply();
                    finish();
                }
                else if (response.code() == 409) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.INVISIBLE);
                        emailLayout.setError(getString(R.string.registeractivity_account_exists));
                        emailLayout.setErrorEnabled(true);
                    });
                }
            }
        });
    }
}
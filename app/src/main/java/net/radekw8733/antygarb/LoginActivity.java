package net.radekw8733.antygarb;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import com.google.android.material.color.DynamicColors;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        DynamicColors.applyToActivityIfAvailable(this);
    }
}
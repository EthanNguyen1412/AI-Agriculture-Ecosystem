package com.nckh.drplant;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class RegisterActivity extends AppCompatActivity {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private TextInputLayout emailLayout, passwordLayout, confirmLayout;
    private TextInputEditText emailInput, passwordInput, confirmInput;
    private Button registerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmLayout = findViewById(R.id.confirmLayout);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmInput = findViewById(R.id.confirmInput);
        registerButton = findViewById(R.id.registerButton);
        TextView loginLink = findViewById(R.id.loginLink);

        registerButton.setOnClickListener(v -> attemptRegister());
        loginLink.setOnClickListener(v -> finish());
    }

    // Kiểm tra dữ liệu nhập; hợp lệ thì chuyển sang màn nhập mã OTP.
    // Tài khoản CHỈ được tạo sau khi nhập đúng mã (xem OtpVerifyActivity).
    private void attemptRegister() {
        String email = getText(emailInput);
        String password = getText(passwordInput);
        String confirm = getText(confirmInput);

        emailLayout.setError(null);
        passwordLayout.setError(null);
        confirmLayout.setError(null);

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError(getString(R.string.auth_error_invalid_email));
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            passwordLayout.setError(getString(R.string.auth_error_short_password));
            return;
        }
        if (!password.equals(confirm)) {
            confirmLayout.setError(getString(R.string.auth_error_password_mismatch));
            return;
        }

        Intent intent = new Intent(this, OtpVerifyActivity.class);
        intent.putExtra(OtpVerifyActivity.EXTRA_EMAIL, email);
        intent.putExtra(OtpVerifyActivity.EXTRA_PASSWORD, password);
        startActivity(intent);
    }

    private static String getText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}

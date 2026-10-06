package com.nckh.drplant;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

public class RegisterActivity extends AppCompatActivity {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private FirebaseAuth firebaseAuth;
    private TextInputLayout emailLayout, passwordLayout, confirmLayout;
    private TextInputEditText emailInput, passwordInput, confirmInput;
    private Button registerButton;
    private ProgressBar loadingBar;

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

        firebaseAuth = FirebaseAuth.getInstance();

        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmLayout = findViewById(R.id.confirmLayout);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmInput = findViewById(R.id.confirmInput);
        registerButton = findViewById(R.id.registerButton);
        loadingBar = findViewById(R.id.loadingBar);
        TextView loginLink = findViewById(R.id.loginLink);

        registerButton.setOnClickListener(v -> attemptRegister());
        loginLink.setOnClickListener(v -> finish());
    }

    // Kiểm tra dữ liệu nhập rồi tạo tài khoản mới trên Firebase.
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

        setLoading(true);
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        // Đăng ký xong Firebase tự đăng nhập luôn, vào thẳng màn hình chính.
                        Toast.makeText(this, R.string.auth_register_success, Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(this, CameraActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        showRegisterError(task.getException());
                    }
                });
    }

    private void showRegisterError(Exception exception) {
        if (exception instanceof FirebaseAuthUserCollisionException) {
            emailLayout.setError(getString(R.string.auth_error_email_in_use));
        } else if (exception instanceof FirebaseAuthWeakPasswordException) {
            passwordLayout.setError(getString(R.string.auth_error_short_password));
        } else if (exception instanceof FirebaseNetworkException) {
            Toast.makeText(this, R.string.auth_error_network, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.auth_error_register_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void setLoading(boolean loading) {
        loadingBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        registerButton.setEnabled(!loading);
    }

    private static String getText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}

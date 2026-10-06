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
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth firebaseAuth;
    private TextInputLayout emailLayout, passwordLayout;
    private TextInputEditText emailInput, passwordInput;
    private Button loginButton;
    private ProgressBar loadingBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        firebaseAuth = FirebaseAuth.getInstance();

        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        loadingBar = findViewById(R.id.loadingBar);
        TextView registerLink = findViewById(R.id.registerLink);
        TextView forgotPasswordLink = findViewById(R.id.forgotPasswordLink);

        loginButton.setOnClickListener(v -> attemptLogin());
        registerLink.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
        forgotPasswordLink.setOnClickListener(v -> sendPasswordReset());
    }

    // Nếu đã đăng nhập từ trước thì vào thẳng màn hình chính.
    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        if (currentUser != null) {
            goToCamera();
        }
    }

    // Kiểm tra dữ liệu nhập rồi gọi Firebase để đăng nhập.
    private void attemptLogin() {
        String email = getText(emailInput);
        String password = getText(passwordInput);

        emailLayout.setError(null);
        passwordLayout.setError(null);

        if (!isValidEmail(email)) {
            emailLayout.setError(getString(R.string.auth_error_invalid_email));
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordLayout.setError(getString(R.string.auth_error_empty_password));
            return;
        }

        setLoading(true);
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        goToCamera();
                    } else {
                        showLoginError(task.getException());
                    }
                });
    }

    // Gửi email đặt lại mật khẩu đến địa chỉ trong ô email.
    private void sendPasswordReset() {
        String email = getText(emailInput);
        emailLayout.setError(null);

        if (!isValidEmail(email)) {
            emailLayout.setError(getString(R.string.auth_error_invalid_email));
            return;
        }

        setLoading(true);
        firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(this, R.string.auth_reset_email_sent, Toast.LENGTH_LONG).show();
                    } else {
                        showLoginError(task.getException());
                    }
                });
    }

    private void showLoginError(Exception exception) {
        int messageRes;
        if (exception instanceof FirebaseNetworkException) {
            messageRes = R.string.auth_error_network;
        } else {
            // Firebase gộp "sai mật khẩu" và "không có tài khoản" thành một lỗi để bảo mật.
            messageRes = R.string.auth_error_login_failed;
        }
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show();
    }

    private void setLoading(boolean loading) {
        loadingBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
    }

    private void goToCamera() {
        Intent intent = new Intent(this, CameraActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private static String getText(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}

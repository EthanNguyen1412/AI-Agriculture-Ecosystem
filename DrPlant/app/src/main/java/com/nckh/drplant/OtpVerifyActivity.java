package com.nckh.drplant;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
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
import com.nckh.drplant.Utils.MailSender;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Màn hình nhập mã OTP khi đăng ký.
 * Luồng: RegisterActivity kiểm tra dữ liệu -> màn này gửi mã qua email -> người dùng nhập đúng mã
 * -> mới tạo tài khoản Firebase -> đăng xuất -> quay về màn Đăng nhập.
 */
public class OtpVerifyActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL = "extra_email";
    public static final String EXTRA_PASSWORD = "extra_password";

    /**
     * true  = hiện mã OTP ngay trên màn hình app (dùng khi demo, không cần gửi email).
     * false = gửi mã qua email Gmail (cần cấu hình mail.sender / mail.password trong local.properties).
     */
    private static final boolean SHOW_OTP_IN_APP = false;

    private static final int OTP_LENGTH = 6;
    private static final long OTP_VALID_MS = 5 * 60 * 1000L;
    private static final long RESEND_COOLDOWN_MS = 60 * 1000L;
    private static final int MAX_ATTEMPTS = 5;

    private static final String STATE_OTP = "state_otp";
    private static final String STATE_EXPIRES_LEFT = "state_expires_left";
    private static final String STATE_ATTEMPTS = "state_attempts";
    private static final String STATE_RESEND_LEFT = "state_resend_left";

    private final SecureRandom random = new SecureRandom();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private FirebaseAuth firebaseAuth;
    private String email;
    private String password;

    private TextInputLayout otpLayout;
    private TextInputEditText otpInput;
    private Button verifyButton;
    private ProgressBar loadingBar;
    private TextView tvResend;
    private TextView tvOtpDemo;

    // Trạng thái mã OTP (chỉ giữ trong bộ nhớ, không lưu ra ổ đĩa).
    private String otpCode;
    private long otpExpiresAt;      // mốc thời gian theo SystemClock.elapsedRealtime()
    private long resendAvailableAt; // mốc thời gian theo SystemClock.elapsedRealtime()
    private int attempts;
    private boolean busy;
    private CountDownTimer resendTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_otp_verify);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        email = getIntent().getStringExtra(EXTRA_EMAIL);
        password = getIntent().getStringExtra(EXTRA_PASSWORD);
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            finish();
            return;
        }

        firebaseAuth = FirebaseAuth.getInstance();

        otpLayout = findViewById(R.id.otpLayout);
        otpInput = findViewById(R.id.otpInput);
        verifyButton = findViewById(R.id.btnOtpVerify);
        loadingBar = findViewById(R.id.otpLoading);
        tvResend = findViewById(R.id.tvOtpResend);
        tvOtpDemo = findViewById(R.id.tvOtpDemo);
        if (SHOW_OTP_IN_APP) {
            findViewById(R.id.tvOtpSpamHint).setVisibility(View.GONE);
        }
        ((TextView) findViewById(R.id.tvOtpEmail)).setText(email);

        verifyButton.setOnClickListener(v -> verifyOtp());
        tvResend.setOnClickListener(v -> {
            if (canResend()) {
                sendOtp();
            }
        });
        findViewById(R.id.tvOtpBack).setOnClickListener(v -> finish());

        if (savedInstanceState != null) {
            otpCode = savedInstanceState.getString(STATE_OTP);
            long now = SystemClock.elapsedRealtime();
            otpExpiresAt = now + savedInstanceState.getLong(STATE_EXPIRES_LEFT);
            resendAvailableAt = now + savedInstanceState.getLong(STATE_RESEND_LEFT);
            attempts = savedInstanceState.getInt(STATE_ATTEMPTS);
        }

        if (otpCode == null) {
            sendOtp();
        } else {
            showOtpInApp(otpCode);
            startResendTimer();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        long now = SystemClock.elapsedRealtime();
        outState.putString(STATE_OTP, otpCode);
        outState.putLong(STATE_EXPIRES_LEFT, Math.max(0, otpExpiresAt - now));
        outState.putLong(STATE_RESEND_LEFT, Math.max(0, resendAvailableAt - now));
        outState.putInt(STATE_ATTEMPTS, attempts);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        executor.shutdownNow();
    }

    // Tạo mã mới rồi gửi về email trên luồng nền.
    private void sendOtp() {
        if (busy) {
            return;
        }
        final String code = String.format(Locale.US, "%0" + OTP_LENGTH + "d", random.nextInt(1_000_000));
        long now = SystemClock.elapsedRealtime();
        otpCode = code;
        otpExpiresAt = now + OTP_VALID_MS;
        resendAvailableAt = now + RESEND_COOLDOWN_MS;
        attempts = 0;
        otpInput.setText("");
        otpLayout.setError(null);

        if (SHOW_OTP_IN_APP) {
            // Chế độ demo: hiện mã ngay trên màn hình, không gửi email.
            showOtpInApp(code);
            startResendTimer();
            return;
        }

        setBusy(true);
        startResendTimer();

        executor.execute(() -> {
            final int result = MailSender.sendOtp(email, code);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setBusy(false);
                if (result == MailSender.OK) {
                    Toast.makeText(this, R.string.otp_sent, Toast.LENGTH_SHORT).show();
                } else {
                    // Gửi lỗi thì bỏ mã này và cho phép gửi lại ngay.
                    otpCode = null;
                    resendAvailableAt = 0;
                    startResendTimer();
                    Toast.makeText(this,
                            result == MailSender.NOT_CONFIGURED
                                    ? R.string.otp_not_configured
                                    : R.string.otp_send_failed,
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    // Hiện mã OTP trong khung xanh trên màn hình (chỉ khi SHOW_OTP_IN_APP = true).
    private void showOtpInApp(String code) {
        if (!SHOW_OTP_IN_APP || code == null) {
            return;
        }
        tvOtpDemo.setText(getString(R.string.otp_demo_banner, code));
        tvOtpDemo.setVisibility(View.VISIBLE);
    }

    // Kiểm tra mã người dùng nhập; đúng mới tạo tài khoản.
    private void verifyOtp() {
        if (busy) {
            return;
        }
        otpLayout.setError(null);

        String input = otpInput.getText() == null ? "" : otpInput.getText().toString().trim();
        if (input.length() != OTP_LENGTH) {
            otpLayout.setError(getString(R.string.otp_error_length));
            return;
        }
        if (otpCode == null) {
            otpLayout.setError(getString(R.string.otp_error_no_code));
            return;
        }
        if (SystemClock.elapsedRealtime() > otpExpiresAt) {
            otpLayout.setError(getString(R.string.otp_error_expired));
            return;
        }
        if (attempts >= MAX_ATTEMPTS) {
            otpLayout.setError(getString(R.string.otp_error_too_many));
            return;
        }

        boolean correct = MessageDigest.isEqual(
                input.getBytes(StandardCharsets.UTF_8),
                otpCode.getBytes(StandardCharsets.UTF_8));
        if (!correct) {
            attempts++;
            int left = MAX_ATTEMPTS - attempts;
            otpLayout.setError(left > 0
                    ? getString(R.string.otp_error_wrong, left)
                    : getString(R.string.otp_error_too_many));
            return;
        }

        createAccount();
    }

    // Mã đúng: tạo tài khoản, đăng xuất, rồi quay về màn Đăng nhập.
    private void createAccount() {
        setBusy(true);
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    setBusy(false);
                    if (task.isSuccessful()) {
                        otpCode = null;
                        // Firebase tự đăng nhập sau khi tạo tài khoản, đăng xuất để bắt đăng nhập lại.
                        firebaseAuth.signOut();
                        Toast.makeText(this, R.string.otp_register_done, Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        showCreateError(task.getException());
                    }
                });
    }

    private void showCreateError(Exception exception) {
        if (exception instanceof FirebaseAuthUserCollisionException) {
            Toast.makeText(this, R.string.auth_error_email_in_use, Toast.LENGTH_LONG).show();
            finish(); // quay lại màn Đăng ký để đổi email
        } else if (exception instanceof FirebaseAuthWeakPasswordException) {
            Toast.makeText(this, R.string.auth_error_short_password, Toast.LENGTH_LONG).show();
            finish();
        } else if (exception instanceof FirebaseNetworkException) {
            Toast.makeText(this, R.string.auth_error_network, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.auth_error_register_failed, Toast.LENGTH_LONG).show();
        }
    }

    private boolean canResend() {
        return !busy && SystemClock.elapsedRealtime() >= resendAvailableAt;
    }

    private void setBusy(boolean value) {
        busy = value;
        loadingBar.setVisibility(value ? View.VISIBLE : View.GONE);
        verifyButton.setEnabled(!value);
        if (!value) {
            updateResendLabel();
        } else {
            tvResend.setEnabled(false);
            tvResend.setAlpha(0.5f);
        }
    }

    // Đếm ngược thời gian chờ để được gửi lại mã.
    private void startResendTimer() {
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        long remaining = resendAvailableAt - SystemClock.elapsedRealtime();
        if (remaining <= 0) {
            updateResendLabel();
            return;
        }
        resendTimer = new CountDownTimer(remaining, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                updateResendLabel();
            }

            @Override
            public void onFinish() {
                updateResendLabel();
            }
        }.start();
        updateResendLabel();
    }

    private void updateResendLabel() {
        long remainingMs = resendAvailableAt - SystemClock.elapsedRealtime();
        if (remainingMs > 0) {
            int seconds = (int) Math.ceil(remainingMs / 1000.0);
            tvResend.setText(getString(R.string.otp_resend_wait, seconds));
        } else {
            tvResend.setText(R.string.otp_resend);
        }
        boolean enabled = canResend();
        tvResend.setEnabled(enabled);
        tvResend.setAlpha(enabled ? 1f : 0.5f);
    }
}

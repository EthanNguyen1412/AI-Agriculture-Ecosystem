package com.nckh.drplant.Utils;

import android.util.Log;

import com.nckh.drplant.BuildConfig;

import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * Gửi email mã OTP qua SMTP của Gmail.
 * Tài khoản gửi được cấu hình trong DrPlant/local.properties (mail.sender, mail.password).
 * Hàm sendOtp chạy mạng nên PHẢI gọi trên luồng nền, không gọi trên luồng giao diện.
 */
public final class MailSender {

    public static final int OK = 0;
    public static final int NOT_CONFIGURED = 1;
    public static final int FAILED = 2;

    private static final String TAG = "MailSender";

    private MailSender() {
        // Utility class
    }

    public static int sendOtp(String toEmail, String code) {
        final String sender = BuildConfig.MAIL_SENDER.trim();
        // Mật khẩu ứng dụng của Google hay có dấu cách giữa các nhóm 4 ký tự, bỏ hết đi.
        final String password = BuildConfig.MAIL_PASSWORD.replace(" ", "");
        if (sender.isEmpty() || password.isEmpty()) {
            return NOT_CONFIGURED;
        }

        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.port", "587");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.ssl.protocols", "TLSv1.2");
            props.put("mail.smtp.connectiontimeout", "15000");
            props.put("mail.smtp.timeout", "15000");
            props.put("mail.smtp.writetimeout", "15000");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(sender, password);
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(sender, "DrPlant", "UTF-8"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, false));
            message.setSubject("[DrPlant] Mã xác nhận đăng ký", "UTF-8");
            message.setText(
                    "Xin chào,\n\n"
                            + "Mã xác nhận (OTP) đăng ký tài khoản DrPlant của bạn là:\n\n"
                            + "        " + code + "\n\n"
                            + "Mã có hiệu lực trong 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.\n"
                            + "Nếu bạn không thực hiện đăng ký, hãy bỏ qua email này.\n\n"
                            + "DrPlant",
                    "UTF-8");

            Transport.send(message);
            return OK;
        } catch (Exception e) {
            Log.e(TAG, "Gửi email OTP thất bại", e);
            return FAILED;
        }
    }
}

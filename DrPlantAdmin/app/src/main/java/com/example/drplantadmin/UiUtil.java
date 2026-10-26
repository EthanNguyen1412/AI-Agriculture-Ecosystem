package com.example.drplantadmin;


import android.content.Context;
import android.text.Editable;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.textfield.TextInputEditText;
import com.example.drplantadmin.data.AdminRepository;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class UiUtil {

    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"));
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN"));

    private UiUtil() { }

    /** Tô màu nhãn trạng thái: Khỏe / Bệnh / Chưa chẩn đoán. */
    public static void bindStatus(TextView pill, int status) {
        Context c = pill.getContext();
        switch (status) {
            case AdminRepository.STATUS_SICK:
                pill.setText("Bệnh");
                pill.setBackgroundResource(R.drawable.bg_pill_sick);
                pill.setTextColor(ContextCompat.getColor(c, R.color.danger));
                break;
            case AdminRepository.STATUS_HEALTHY:
                pill.setText("Khỏe");
                pill.setBackgroundResource(R.drawable.bg_pill_healthy);
                pill.setTextColor(ContextCompat.getColor(c, R.color.primary));
                break;
            default:
                pill.setText("Chưa chẩn đoán");
                pill.setBackgroundResource(R.drawable.bg_pill_none);
                pill.setTextColor(ContextCompat.getColor(c, R.color.text_secondary));
                break;
        }
    }

    public static String formatTime(long millis) {
        return TIME_FORMAT.format(new Date(millis));
    }

    public static String formatDate(long millis) {
        return DATE_FORMAT.format(new Date(millis));
    }

    public static String percent(int part, int total) {
        if (total == 0) return "0%";
        return String.format(Locale.US, "%.0f%%", 100.0 * part / total);
    }

    public static String text(TextInputEditText input) {
        Editable e = input.getText();
        return e == null ? "" : e.toString().trim();
    }
}

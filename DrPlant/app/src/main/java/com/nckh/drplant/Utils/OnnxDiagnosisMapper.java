package com.nckh.drplant.Utils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.nckh.drplant.AiUtils.OnnxDiagnosisEngine;
import com.nckh.drplant.Models.DiagnosisResponse;
public final class OnnxDiagnosisMapper {
    private static final String COLOR_DANGER = "#ef4444";
    private static final String COLOR_WARNING = "#f59e0b";
    private static final String COLOR_SUCCESS = "#22c55e";
    private OnnxDiagnosisMapper() {
        // Utility class
    }
    // Chuyển detection tốt nhất từ model ONNX thành cấu trúc DiagnosisResponse mà UI hiện tại đang sử dụng.
    @NonNull
    public static DiagnosisResponse mapToDiagnosisResponse(@Nullable OnnxDiagnosisEngine.DetectionResult detectionResult) {
        if (detectionResult == null) {
            DiagnosisResponse emptyResponse = new DiagnosisResponse();
            emptyResponse.success = false;
            emptyResponse.message = "⚠️ Không phát hiện vùng lá phù hợp. Vui lòng chụp gần hơn hoặc chọn ảnh rõ nét hơn.";
            return emptyResponse;
        }
        DiseaseProfile profile = createProfile(detectionResult);
        DiagnosisResponse response = new DiagnosisResponse();
        response.success = true;
        response.message = detectionResult.healthy ? "✅ Không phát hiện bệnh" : "⚠️ Phát hiện bệnh";
        response.prediction = createPrediction(detectionResult);
        response.disease = createDisease(profile);
        response.solutions = createSolutions(profile);
        return response;
    }
    // Tạo phần prediction để giữ lại nhãn class, độ tin cậy và loại cây cho lịch sử cùng màn hình chi tiết.
    @NonNull
    private static DiagnosisResponse.Prediction createPrediction(@NonNull OnnxDiagnosisEngine.DetectionResult detectionResult) {
        DiagnosisResponse.Prediction prediction = new DiagnosisResponse.Prediction();
        prediction.classId = detectionResult.className;
        prediction.classIndex = detectionResult.classIndex;
        prediction.className = detectionResult.className;
        prediction.confidence = detectionResult.confidence;
        prediction.plantType = detectionResult.plantType;
        prediction.isHealthy = detectionResult.healthy;
        return prediction;
    }
    // Tạo phần thông tin bệnh chi tiết từ metadata được ánh xạ theo class của model.
    @NonNull
    private static DiagnosisResponse.Disease createDisease(@NonNull DiseaseProfile profile) {
        DiagnosisResponse.Disease disease = new DiagnosisResponse.Disease();
        disease.name = profile.diseaseName;
        disease.symptoms = profile.symptoms;
        disease.causes = profile.causes;
        disease.severity = createSeverity(profile);
        return disease;
    }
    // Tạo thông tin mức độ nghiêm trọng để hiển thị badge màu trên màn hình kết quả.
    @NonNull
    private static DiagnosisResponse.Severity createSeverity(@NonNull DiseaseProfile profile) {
        DiagnosisResponse.Severity severity = new DiagnosisResponse.Severity();
        severity.level = profile.severityLevel;
        severity.label = profile.severityLabel;
        severity.color = profile.severityColor;
        severity.icon = profile.severityIcon;
        return severity;
    }
    // Tạo phần giải pháp hóa học và sinh học từ dữ liệu mô tả đã chuẩn hóa theo từng nhãn bệnh.
    @NonNull
    private static DiagnosisResponse.Solutions createSolutions(@NonNull DiseaseProfile profile) {
        DiagnosisResponse.Solutions solutions = new DiagnosisResponse.Solutions();
        solutions.chemical = createSolution("Giải pháp Hóa học", "🧪", profile.chemicalSolution);
        solutions.biological = createSolution("Giải pháp Sinh học", "🌱", profile.biologicalSolution);
        return solutions;
    }
    // Tạo một block giải pháp duy nhất để UI có thể tái sử dụng cho nhiều nhóm xử lý khác nhau.
    @NonNull
    private static DiagnosisResponse.Solution createSolution(@NonNull String title,
                                                             @NonNull String icon,
                                                             @NonNull String description) {
        DiagnosisResponse.Solution solution = new DiagnosisResponse.Solution();
        solution.title = title;
        solution.icon = icon;
        solution.description = description;
        return solution;
    }
    // Ánh xạ class của model sang mô tả bệnh/thông tin chăm sóc để người dùng xem được kết quả có ý nghĩa.
    @NonNull
    private static DiseaseProfile createProfile(@NonNull OnnxDiagnosisEngine.DetectionResult detectionResult) {
        switch (detectionResult.className) {
            case "cafe_gisat":
                return new DiseaseProfile(
                        "Bệnh Gỉ Sắt Cà Phê",
                        "high",
                        "Cao",
                        COLOR_DANGER,
                        "⚠️",
                        "Mặt dưới lá có các ổ bột màu vàng cam hoặc nâu gỉ. Lá vàng dần, khô và rụng sớm làm giảm khả năng quang hợp.",
                        "Thường do nấm Hemileia vastatrix phát triển mạnh khi vườn ẩm độ cao, thiếu thông thoáng, mưa kéo dài hoặc cây suy yếu.",
                        "Ưu tiên luân phiên thuốc đặc trị nấm gỉ sắt theo khuyến cáo địa phương, phun đúng liều lượng và đúng thời điểm khi bệnh mới xuất hiện.",
                        "Tỉa cành cho vườn thông thoáng, bón phân cân đối, thu gom lá bệnh và theo dõi thường xuyên trong mùa mưa."
                );
            case "cafe_dommatcua":
                return new DiseaseProfile(
                        "Bệnh Đốm Mắt Cua Cà Phê",
                        "medium",
                        "Trung bình",
                        COLOR_WARNING,
                        "⚠️",
                        "Lá xuất hiện đốm tròn hoặc bầu dục màu nâu, ở giữa nhạt màu giống mắt cua; nặng hơn có thể làm lá vàng và rụng.",
                        "Bệnh thường liên quan nấm Cercospora, phát sinh khi cây thiếu dinh dưỡng, đất khô hạn xen kẽ ẩm độ cao hoặc tán lá kém thông thoáng.",
                        "Có thể dùng thuốc nấm phổ rộng theo khuyến cáo, ưu tiên xử lý sớm khi số lá bệnh còn ít để hạn chế lây lan.",
                        "Bổ sung dinh dưỡng cân đối, tưới nước hợp lý, tỉa cành và vệ sinh vườn nhằm giảm áp lực nguồn bệnh."
                );
            case "cafe_khoe":
                return createHealthyProfile("Cà phê", "Cây cà phê hiện chưa ghi nhận dấu hiệu bệnh rõ rệt trên lá.");
            case "saurieng_chayla":
                return new DiseaseProfile(
                        "Bệnh Cháy Lá / Chết Đọt Sầu Riêng",
                        "high",
                        "Cao",
                        COLOR_DANGER,
                        "⚠️",
                        "Lá non có đốm nâu, cháy khô từ mép lá lan vào trong; chồi non héo, ngọn khô chết từ đỉnh xuống và có thể xuất hiện chảy nhựa nâu ở chồi bị bệnh.",
                        "Bệnh thường liên quan Phytophthora palmivora và một số nấm cơ hội khác, phát triển mạnh trong mùa mưa, đất úng nước, ẩm độ cao hoặc khi cây suy kiệt.",
                        "Ưu tiên hoạt chất lưu dẫn để xử lý Phytophthora theo khuyến cáo chuyên môn, kết hợp luân phiên thuốc để giảm nguy cơ kháng thuốc.",
                        "Bắt buộc cải thiện thoát nước, vệ sinh lá bệnh, tỉa cành cho tán thông thoáng, bón vôi khi đất chua và có thể bổ sung Trichoderma vào đầu mùa mưa."
                );
            case "saurieng_domtao":
                return new DiseaseProfile(
                        "Bệnh Đốm Tảo Sầu Riêng",
                        "medium",
                        "Trung bình",
                        COLOR_WARNING,
                        "⚠️",
                        "Trên lá xuất hiện các đốm tròn hơi nổi, màu xám xanh đến nâu đỏ; khi nặng có thể làm giảm diện tích quang hợp của lá.",
                        "Bệnh thường liên quan tảo ký sinh phát triển mạnh trong điều kiện ẩm cao, mưa nhiều, tán lá dày và ánh sáng kém.",
                        "Có thể xử lý bằng thuốc gốc đồng hoặc sản phẩm phù hợp theo hướng dẫn kỹ thuật tại địa phương.",
                        "Tỉa cành cho thông thoáng, giảm ẩm trong tán, vệ sinh lá bệnh và theo dõi thường xuyên sau các đợt mưa dài ngày."
                );
            case "saurieng_khoe":
                return createHealthyProfile("Sầu riêng", "Lá sầu riêng hiện chưa ghi nhận dấu hiệu bệnh rõ rệt.");
            default:
                return new DiseaseProfile(
                        formatDisplayName(detectionResult.className),
                        detectionResult.healthy ? "low" : "medium",
                        detectionResult.healthy ? "Thấp" : "Trung bình",
                        detectionResult.healthy ? COLOR_SUCCESS : COLOR_WARNING,
                        detectionResult.healthy ? "✅" : "⚠️",
                        detectionResult.healthy
                                ? "Không phát hiện biểu hiện bất thường rõ ràng trên lá ở ảnh hiện tại."
                                : "Đã phát hiện vùng lá bất thường nhưng chưa có mô tả chi tiết cho nhãn này.",
                        detectionResult.healthy
                                ? "Không ghi nhận nguyên nhân gây bệnh từ kết quả hiện tại."
                                : "Cần đối chiếu thêm với chuyên gia hoặc dữ liệu bệnh chi tiết để xác định nguyên nhân chính xác hơn.",
                        detectionResult.healthy
                                ? "Chưa cần xử lý hóa học khi cây vẫn khỏe mạnh."
                                : "Chỉ sử dụng thuốc khi cần thiết và theo khuyến cáo của cán bộ kỹ thuật địa phương.",
                        detectionResult.healthy
                                ? "Tiếp tục chăm sóc, tưới nước và bón phân cân đối để duy trì sức khỏe cây."
                                : "Theo dõi thêm trên vườn thực tế, vệ sinh lá bệnh và cải thiện điều kiện canh tác để hạn chế bệnh phát triển."
                );
        }
    }
    // Tạo metadata chuẩn cho các class khỏe mạnh để giao diện vẫn hiển thị đầy đủ nhưng thân thiện với người dùng.
    @NonNull
    private static DiseaseProfile createHealthyProfile(@NonNull String plantName, @NonNull String symptomSummary) {
        return new DiseaseProfile(
                "Cây " + plantName + " khỏe mạnh",
                "low",
                "Thấp",
                COLOR_SUCCESS,
                "✅",
                symptomSummary,
                "Không phát hiện nguyên nhân gây bệnh từ ảnh hiện tại.",
                "Chưa cần áp dụng giải pháp hóa học khi cây vẫn khỏe mạnh.",
                "Duy trì tưới tiêu, dinh dưỡng và vệ sinh vườn hợp lý để phòng bệnh chủ động."
        );
    }
    // Chuẩn hóa nhãn thô của model thành dạng dễ đọc hơn khi cần dùng làm tiêu đề dự phòng.
    @NonNull
    private static String formatDisplayName(@Nullable String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return "Chưa có thông tin";
        }
        return rawValue.replace('_', ' ');
    }
    // Lớp dữ liệu nội bộ dùng gom toàn bộ nội dung hiển thị cho một nhãn bệnh cụ thể.
    private static final class DiseaseProfile {
        final String diseaseName;
        final String severityLevel;
        final String severityLabel;
        final String severityColor;
        final String severityIcon;
        final String symptoms;
        final String causes;
        final String chemicalSolution;
        final String biologicalSolution;
        DiseaseProfile(@NonNull String diseaseName,
                       @NonNull String severityLevel,
                       @NonNull String severityLabel,
                       @NonNull String severityColor,
                       @NonNull String severityIcon,
                       @NonNull String symptoms,
                       @NonNull String causes,
                       @NonNull String chemicalSolution,
                       @NonNull String biologicalSolution) {
            this.diseaseName = diseaseName;
            this.severityLevel = severityLevel;
            this.severityLabel = severityLabel;
            this.severityColor = severityColor;
            this.severityIcon = severityIcon;
            this.symptoms = symptoms;
            this.causes = causes;
            this.chemicalSolution = chemicalSolution;
            this.biologicalSolution = biologicalSolution;
        }
    }
}
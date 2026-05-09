from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from fastapi.responses import JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from typing import Optional
import io
from PIL import Image
import uvicorn
import csv
from pathlib import Path
from ultralytics import YOLO
# ============================================
# KHỞI TẠO FASTAPI
# ============================================
app = FastAPI(
    title="Plant Disease Diagnosis API - Coffee & Durian",
    description="API chẩn đoán bệnh cho 6 lớp: 3 cà phê (gisat, dommatcua, khoe) + 3 sầu riêng (chayla, domtao, khoe)",
    version="2.1.0" # Đã nâng cấp
)

# CORS middleware để frontend có thể gọi API
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ============================================
# CONFIGURATION - Từ config.yaml
# ============================================
CLASS_MAPPING = {
    0: "cafe_gisat",
    1: "cafe_dommatcua", 
    2: "cafe_khoe",
    3: "saurieng_chayla",
    4: "saurieng_domtao",
    5: "saurieng_khoe"
}

# VALID_PLANT_TYPES đã bị xóa vì không cần nữa

# ============================================
# DATABASE - Load từ Cẩm nang.csv
# ============================================
def load_handbook_database(csv_path: str = "Cẩm_nang.csv") -> dict:
    """
    Load dữ liệu từ file CSV Cẩm nang
    
    Returns:
        dict: {class_id: {thông tin chi tiết}}
    """
    database = {}
    
    try:
        with open(csv_path, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                class_id = row['class_id']
                database[class_id] = {
                    "disease_name": row['ten_benh'],
                    "symptoms": row['trieu_chung'],
                    "causes": row['nguyen_nhan'],
                    "chemical_solution": row['giai_phap_hoa_hoc'],
                    "biological_solution": row['giai_phap_sinh_hoc']
                }
        print(f"Đã load {len(database)} records từ Cẩm nang")
        return database
    except FileNotFoundError:
        print("Không tìm thấy file Cẩm_nang.csv, sử dụng mock data")
        return create_mock_database()

def create_mock_database() -> dict:
    """Mock database khi không có file CSV"""
    # (Giữ nguyên hàm này)
    return {
        "cafe_gisat": {
            "disease_name": "Bệnh Gỉ Sắt (Rỉ Sắt) Cà Phê",
            "symptoms": "Xuất hiện ở mặt dưới lá già, ban đầu là đốm nhỏ màu vàng nhạt giống 'giọt dầu'. Sau đó xuất hiện lớp bột màu vàng cam (như gỉ sắt) ở mặt dưới lá.",
            "causes": "Nấm Hemileia vastatrix. Phát triển mạnh vào mùa mưa, độ ẩm cao, vườn rậm rạp.",
            "chemical_solution": "Phun phòng: Gốc Đồng (Copper Hydroxide, Copper Oxychloride), Mancozeb. Phun trị: Nhóm Triazole (Hexaconazole, Propiconazole, Difenoconazole). Phun 2-3 lần, cách 10-15 ngày.",
            "biological_solution": "Lựa chọn giống kháng bệnh, tạo tán thông thoáng bằng cắt tỉa cành, bón phân cân đối (tăng Kali, giảm Đạm), làm cỏ thường xuyên."
        },
        "cafe_dommatcua": {
            "disease_name": "Bệnh Đốm Mắt Cua (Đốm Nâu)",
            "symptoms": "Vết bệnh hình tròn có nhiều vòng đồng tâm giống 'mắt cua': giữa xám có chấm đen, xung quanh nâu đỏ, ngoài cùng vàng. Tâm vết khô và rách tạo lỗ thủng.",
            "causes": "Nấm Cercospora coffeicola. Bệnh cơ hội phát sinh ở vườn chăm sóc kém, thiếu phân, cây stress do thiếu nước, đất cằn cỗi.",
            "chemical_solution": "Mancozeb, Chlorothalonil, Copper Oxychloride (phòng). Nhóm Triazole: Hexaconazole, Propiconazole, Difenoconazole (trị). Phun 2-3 lần, cách 2 tuần.",
            "biological_solution": "Bón phân cân đối đầy đủ (ưu tiên 1), tưới nước hợp lý để giải quyết stress, tỉa cành và vệ sinh vườn, cải tạo đất bằng phân hữu cơ + nấm Trichoderma."
        },
        "cafe_khoe": {
            "disease_name": "Cây Cà Phê Khỏe Mạnh",
            "symptoms": "Lá xanh đậm, dày, bóng mượt. Tán phát triển cân đối, thông thoáng. Sinh trưởng mạnh mẽ, ra hoa đồng loạt, tỷ lệ đậu quả cao.",
            "causes": "Chọn giống khỏe phù hợp, trồng trên đất tốt, mật độ hợp lý, chăm sóc đúng kỹ thuật.",
            "chemical_solution": "Bón thúc NPK 3 thời điểm: trước ra hoa, sau đậu quả, trước thu hoạch 1 tháng. Tưới nước sau bón phân. Rửa vườn (thuốc gốc Đồng) sau thu hoạch.",
            "biological_solution": "Tưới nước hợp lý (độ ẩm 60-70%), cắt tỉa cành sau thu hoạch và trước ra hoa, làm cỏ + phủ gốc, bón phân chuồng 10-15kg/cây + nấm Trichoderma."
        },
        "saurieng_chayla": {
            "disease_name": "Bệnh Cháy Lá / Chết Đọt Sầu Riêng",
            "symptoms": "Lá non có đốm nâu, cháy khô từ mép lá. Chồi non héo, ngọn khô chết từ đỉnh xuống. Vỏ chồi nứt, chảy nhựa nâu. Cây trụi lá, mất khả năng quang hợp.",
            "causes": "Bệnh phức hợp: Phytophthora palmivora (nguy hiểm nhất, gây thối ngọn khi ngập úng), Lasiodiplodia theobromae, Fusarium. Phát sinh mùa mưa, độ ẩm cao, đất ngập úng, pH < 5.",
            "chemical_solution": "Đặc trị Phytophthora: Metalaxyl, Fosetyl-Aluminium (tưới gốc 5L/cây). Phòng: Mancozeb, Copper Oxychloride. Trị Lasiodiplodia: Azoxystrobin, Hexaconazole. Luân phiên hoạt chất, phun 2-3 lần cách 7 ngày.",
            "biological_solution": "Đào mương thoát nước (quan trọng nhất), bón vôi 500-1000kg/ha nếu pH < 5, bón phân chuồng 5-10 tấn/ha, tưới Trichoderma tháng 8-9, tỉa cành yếu để thông thoáng, thu gom lá bệnh đốt/chôn."
        },
        "saurieng_domtao": {
            "disease_name": "Bệnh Đốm Rong (Đốm Tảo)",
            "symptoms": "Vết bệnh tròn 3-5mm, nổi cộm, có lớp lông nhung màu đỏ nâu/xanh xám, sau chuyển xám nâu khô. Trên thân/cành: chấm xanh lan thành mảng màu xanh rêu.",
            "causes": "Tảo Cephaleuros virescens (không phải nấm). Vườn thiếu chăm sóc, giáp tán, cỏ um tùm, không tỉa cành, mật độ dày, kém thông thoáng, thiếu ánh sáng, độ ẩm cao.",
            "chemical_solution": "Sản phẩm Gốc Đồng (Copper Hydroxide, Copper Oxychloride, Copper Sulfate). Sản phẩm rửa vườn như Giáp đồng, Nano Cu Gold. Phun ướt đẫm toàn bộ thân/cành/lá sau thu hoạch.",
            "biological_solution": "Cắt tỉa cành già/bệnh để tăng ánh sáng và gió (ưu tiên 1), đào rãnh thoát nước, quản lý cỏ, đảm bảo khoảng cách trồng, hạn chế bón quá nhiều đạm."
        },
        "saurieng_khoe": {
            "disease_name": "Cây Sầu Riêng Khỏe Mạnh",
            "symptoms": "Cơi bung đồng loạt, đọt mập đốt vừa, lá to dày xanh bóng, rễ phát triển mạnh, tán cân đối, trái xanh gai to tròn, hạn chế rụng.",
            "causes": "Đất tơi xốp giàu hữu cơ pH ổn định, dinh dưỡng đủ (phân hữu cơ, Humic & Fulvic), tưới nước đủ, tủ gốc giữ ẩm, canh tác theo VietGAP.",
            "chemical_solution": "Theo giai đoạn: (1) Kích cơi: phun đạm cao + amino acid trước ra cơi, bón NPK 30-10-10. (2) Dưỡng cơi: phun vi lượng (Zn, Mg, Ca, B). (3) Đậu trái: phun siêu đậu quả + Bo sau xả nhị 7-10 ngày. (4) Nuôi trái: phun Ca-B tránh nứt cuống.",
            "biological_solution": "Quy trình 4 bước: (1) Bón phân hữu cơ sau cơi già, (2) Tưới Humic kích rễ sau 1 tuần, (3) Bón NPK thúc cơi mới, (4) Phun dưỡng đọt + quản lý sâu khi cơi nhú. Tưới nước hợp lý, tưới cách ngày khi ra hoa."
        }
    }
# ============================================
# AI MODEL FUNCTIONS
# ============================================
def predict_disease(image_pil: Image.Image, model) -> dict:
    # (Giữ nguyên hàm này)
    results = model.predict(image_pil, conf=0.5)
    
    if len(results[0].boxes) > 0:
        class_id = int(results[0].boxes[0].cls[0])
        confidence = float(results[0].boxes[0].conf[0])
        predicted_class = CLASS_MAPPING[class_id]
    else:
        predicted_class = None
        confidence = 0.0
        class_id = -1
    
    return {
        "class_id": predicted_class,
        "confidence": confidence,
        "class_index": class_id
    }

# Load database khi khởi động
HANDBOOK_DB = load_handbook_database()

# ============================================
# AI MODEL - Load model khi khởi động
# ============================================
ai_model = YOLO('best.pt')


def get_solution_from_db(class_id: str) -> dict:
    solution = HANDBOOK_DB.get(class_id)
    
    if not solution:
        return {
            "disease_name": "Không xác định",
            "symptoms": "Không tìm thấy thông tin trong cơ sở dữ liệu",
            "causes": "N/A",
            "chemical_solution": "Vui lòng liên hệ chuyên gia nông nghiệp",
            "biological_solution": "Vui lòng liên hệ chuyên gia nông nghiệp"
        }
    
    return solution

# ============================================
# VALIDATION LOGIC
# ============================================
# XÓA: Hàm validate_prediction(plant_type, predicted_class) đã bị xóa
# ...

def get_severity_level(class_id: str) -> dict:
    # (Giữ nguyên hàm này)
    if "khoe" in class_id:
        return {
            "level": "healthy",
            "label": "Khỏe mạnh",
            "color": "#10b981",  # green
            "icon": "🌿"
        }
    
    disease_severity = {
        "cafe_gisat": {"level": "high", "label": "Cao", "color": "#ef4444", "icon": "⚠️"},
        "cafe_dommatcua": {"level": "medium", "label": "Trung bình", "color": "#f59e0b", "icon": "⚠️"},
        "saurieng_chayla": {"level": "high", "label": "Cao", "color": "#ef4444", "icon": "⚠️"},
        "saurieng_domtao": {"level": "medium", "label": "Trung bình", "color": "#f59e0b", "icon": "⚠️"}
    }
    
    return disease_severity.get(class_id, {
        "level": "unknown",
        "label": "Không xác định",
        "color": "#6b7280",
        "icon": "❓"
    })

# ============================================
# API ENDPOINTS
# ============================================
@app.post("/diagnose")
async def diagnose_plant(
    file: UploadFile = File(..., description="Ảnh lá cây (JPG, PNG)")
    # XÓA: plant_type: str = Form(...) đã bị xóa
):
    """
    API chẩn đoán bệnh cây trồng (Tự động nhận diện)
    
    **Luồng xử lý:**
    1. Nhận ảnh
    2. Chạy AI model (YOLOv8) để dự đoán class
    3. Nếu không thấy: Trả về lỗi 404
    4. Nếu thấy: Trả về thông tin chi tiết từ Cẩm nang
    
    **Args:**
    - file: File ảnh upload
    
    **Returns:**
    - Success: JSON với kết quả + giải pháp chi tiết
    - Error: JSON với thông báo lỗi
    """
    
    # XÓA: Validation cho plant_type đã bị xóa
    
    # Validate file type
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(
            status_code=400,
            detail="File upload phải là ảnh (image/jpeg, image/png, image/jpg)"
        )
    
    try:
        # Đọc file ảnh
        image_bytes = await file.read()
        
        # Validate ảnh hợp lệ
        try:
            image = Image.open(io.BytesIO(image_bytes))
            image.verify()
            
            # Kiểm tra kích thước tối thiểu
            image = Image.open(io.BytesIO(image_bytes))  # Reopen sau verify
            width, height = image.size
            if width < 32 or height < 32:
                raise HTTPException(
                    status_code=400,
                    detail="Ảnh quá nhỏ. Kích thước tối thiểu: 32x32 pixels"
                )
        except Exception as e:
            raise HTTPException(
                status_code=400,
                detail=f"File ảnh không hợp lệ hoặc bị hỏng: {str(e)}"
            )
        
        # BƯỚC 1: Chạy AI model để dự đoán
        prediction = predict_disease(image, ai_model)
        predicted_class = prediction["class_id"]
        confidence = prediction["confidence"]
        class_index = prediction["class_index"]
        
        # BƯỚC 2: XÁC THỰC (MỚI) - Kiểm tra xem AI có tìm thấy gì không
        if not predicted_class:
            return JSONResponse(
                status_code=404, # Dùng 404 Not Found
                content={
                    "success": False,
                    "error": "not_detected",
                    "message": "Không phát hiện được bệnh trong ảnh. Vui lòng chụp lại ảnh rõ hơn hoặc ở nơi đủ sáng.",
                }
            )
        
        # XÓA: Logic validation "mismatch" đã bị xóa

        # BƯỚC 3: Lấy thông tin chi tiết từ Cẩm nang
        handbook_info = get_solution_from_db(predicted_class)
        severity = get_severity_level(predicted_class)
        is_healthy = "khoe" in predicted_class
        
        # THÊM: Tự suy luận plant_type từ kết quả
        detected_plant_type = predicted_class.split("_")[0] # "cafe" hoặc "saurieng"
        
        # BƯỚC 4: Trả về kết quả thành công
        response_data = {
            "success": True,
            "message": "🌿 Cây khỏe mạnh!" if is_healthy else f"{severity['icon']} Phát hiện bệnh",
            
            # Thông tin dự đoán
            "prediction": {
                "class_id": predicted_class,
                "class_index": class_index,
                "class_name": CLASS_MAPPING[class_index],
                "confidence": round(confidence * 100, 1),  # Chuyển sang %
                "plant_type": detected_plant_type, # THÊM trường này
                "is_healthy": is_healthy
            },
            
            # Thông tin bệnh
            "disease": {
                "name": handbook_info["disease_name"],
                "severity": severity,
                "symptoms": handbook_info["symptoms"],
                "causes": handbook_info["causes"]
            },
            
            # Giải pháp điều trị
            "solutions": {
                "chemical": {
                    "title": "Giải pháp Hóa học",
                    "description": handbook_info["chemical_solution"],
                    "icon": "🧪"
                },
                "biological": {
                    "title": "Giải pháp Sinh học",
                    "description": handbook_info["biological_solution"],
                    "icon": "🌱"
                }
            }
        }
        
        # Thêm recommendations nếu là cây khỏe
        if is_healthy:
            response_data["recommendations"] = {
                "title": "Duy trì sức khỏe cây",
                "tips": [
                    "Tiếp tục chăm sóc theo quy trình hiện tại",
                    "Theo dõi thường xuyên để phát hiện sớm bệnh",
                    "Bón phân đúng thời điểm theo giai đoạn sinh trưởng"
                ]
            }
        
        return JSONResponse(
            status_code=200,
            content=response_data
        )
        
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Lỗi server khi xử lý: {str(e)}"
        )

@app.get("/")
async def root():
    """Health check & API info"""
    return {
        "status": "running",
        "api_name": "Plant Disease Diagnosis API",
        "version": "2.1.0", # Cập nhật version
        "supported_plants": ["Cà phê (cafe)", "Sầu riêng (saurieng)"],
        "total_classes": len(CLASS_MAPPING),
        "classes": CLASS_MAPPING,
        "endpoints": {
            "diagnose": "POST /diagnose - Chẩn đoán bệnh từ ảnh (Tự động)",
            "classes": "GET /classes - Danh sách các class",
            "health": "GET /health - Kiểm tra trạng thái API",
            "docs": "GET /docs - API Documentation (Swagger UI)"
        }
    }

@app.get("/classes")
async def get_classes():
    # (Giữ nguyên hàm này)
    classes_info = []
    for idx, class_id in CLASS_MAPPING.items():
        info = HANDBOOK_DB.get(class_id, {})
        classes_info.append({
            "index": idx,
            "class_id": class_id,
            "disease_name": info.get("disease_name", "N/A"),
            "plant_type": class_id.split("_")[0],
            "is_healthy": "khoe" in class_id
        })
    
    return {
        "total": len(classes_info),
        "classes": classes_info
    }

@app.get("/health")
async def health_check():
    # (GiDữ nguyên hàm này)
    return {
        "status": "healthy",
        "model_loaded": ai_model is not None,
        "database_loaded": len(HANDBOOK_DB) > 0,
        "total_diseases": len(HANDBOOK_DB),
        "supported_classes": list(CLASS_MAPPING.values())
    }

# -*- coding: utf-8 -*-
"""Tạo ERD + bảng UI (kiểu Figma) + cập nhật file nội dung thuyết trình."""
import sys
from pathlib import Path

import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
from PIL import Image, ImageDraw, ImageFont
from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.oxml.ns import qn
from docx.shared import Cm, Inches, Pt, RGBColor

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

ROOT = Path(r"C:\AI-Agriculture-Ecosystem\DrPlant")
FIG = ROOT / "report_figures" / "slides"
UI = ROOT / "report_figures" / "ui"
OUT_DOC = ROOT / "NoiDung_ThuyetTrinh_DrPlant.docx"
FIG.mkdir(parents=True, exist_ok=True)

ACCENT = "#1F4E79"
EDGE = "#222222"


def draw_entity(ax, x, y, w, h, title, fields, header="#1F4E79"):
    # header
    ax.add_patch(FancyBboxPatch(
        (x, y + h - 0.55), w, 0.55,
        boxstyle="round,pad=0.01,rounding_size=0.04",
        linewidth=1.2, edgecolor=EDGE, facecolor=header, zorder=2,
    ))
    ax.text(x + w / 2, y + h - 0.28, title, ha="center", va="center",
            fontsize=10, color="white", weight="bold", zorder=3)
    # body
    ax.add_patch(FancyBboxPatch(
        (x, y), w, h - 0.5,
        boxstyle="round,pad=0.01,rounding_size=0.04",
        linewidth=1.2, edgecolor=EDGE, facecolor="#F8F8F8", zorder=1,
    ))
    ax.text(x + 0.12, y + h - 0.85, "\n".join(fields), ha="left", va="top",
            fontsize=8.2, color="#111", family="DejaVu Sans", linespacing=1.45, zorder=3)


def rel(ax, p1, p2, label, rad=0):
    ax.annotate(
        "", xy=p2, xytext=p1,
        arrowprops=dict(arrowstyle="-|>", color=EDGE, lw=1.2,
                        connectionstyle=f"arc3,rad={rad}", shrinkA=2, shrinkB=2),
        zorder=0,
    )
    mx, my = (p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2
    if rad:
        my += rad * 0.8
    ax.text(mx, my + 0.15, label, ha="center", va="bottom", fontsize=7.5,
            color="#333", bbox=dict(boxstyle="round,pad=0.15", fc="white", ec="none", alpha=0.9))


def make_erd():
    fig, ax = plt.subplots(figsize=(12.5, 7.8))
    ax.set_xlim(0, 12.5)
    ax.set_ylim(0, 8)
    ax.axis("off")
    ax.set_title("DrPlant — ERD / Conceptual Data Model", fontsize=14,
                 color=ACCENT, weight="bold", pad=12)

    # Entities
    draw_entity(ax, 0.4, 5.0, 3.2, 2.6, "User (Firebase Auth)", [
        "PK  uid : string",
        "     email : string",
        "     displayName : string?",
        "     createdAt : timestamp",
        "",
        "(managed by Firebase,",
        " not stored in local SQL)",
    ], header="#2E7D32")

    draw_entity(ax, 4.6, 4.6, 3.6, 3.0, "DiagnosisHistoryItem", [
        "PK  id : string",
        "FK  userId : string",
        "     viewedAt : long",
        "     imagePath : string?",
        "     plantType : string",
        "     status : string",
        "     diseaseName : string?",
        "     careNote : string?",
        "     confidence : float?",
    ], header="#1565C0")

    draw_entity(ax, 9.0, 5.2, 3.1, 2.4, "DiagnosisResponse", [
        "     plantType",
        "     status / label",
        "     suggestion",
        "     rawModelOutput?",
        "",
        "(payload in Intent /",
        " embedded in History)",
    ], header="#6A1B9A")

    draw_entity(ax, 0.4, 0.6, 3.2, 2.5, "Area *planned", [
        "PK  areaId",
        "FK  userId",
        "     name",
        "     location?",
        "     note?",
    ], header="#EF6C00")

    draw_entity(ax, 4.6, 0.5, 3.4, 2.6, "Plant *planned", [
        "PK  plantId",
        "FK  areaId",
        "     species (durian/coffee…)",
        "     plantedAt?",
        "     healthStatus?",
    ], header="#EF6C00")

    draw_entity(ax, 9.0, 0.5, 3.1, 2.6, "DiseaseRecord *planned", [
        "PK  recordId",
        "FK  plantId",
        "FK  historyItemId?",
        "     diseaseName",
        "     detectedAt",
        "     severity?",
    ], header="#EF6C00")

    # Relationships
    rel(ax, (3.6, 6.2), (4.6, 6.2), "1  ——  N")
    rel(ax, (8.2, 6.4), (9.0, 6.4), "embeds / maps")
    rel(ax, (2.0, 5.0), (2.0, 3.1), "1 —— N")
    rel(ax, (3.6, 1.8), (4.6, 1.8), "1 —— N")
    rel(ax, (8.0, 1.7), (9.0, 1.7), "1 —— N")

    ax.text(
        6.25, 0.18,
        "Solid / blue-green = implemented in current milestone (Auth + local history JSON).  "
        "Orange = planned for statistics & server sync.",
        ha="center", va="center", fontsize=8.2, color="#444",
    )

    path = FIG / "slide_erd_drplant.png"
    fig.savefig(path, dpi=220, bbox_inches="tight", facecolor="white", pad_inches=0.2)
    plt.close(fig)
    print("ERD:", path)
    return path


def _font(size, bold=False):
    candidates = [
        r"C:\Windows\Fonts\segoeuib.ttf" if bold else r"C:\Windows\Fonts\segoeui.ttf",
        r"C:\Windows\Fonts\arialbd.ttf" if bold else r"C:\Windows\Fonts\arial.ttf",
    ]
    for c in candidates:
        if Path(c).exists():
            return ImageFont.truetype(c, size)
    return ImageFont.load_default()


def phone_frame(img: Image.Image, max_h=720) -> Image.Image:
    """Scale screenshot and put in a simple device frame."""
    ratio = max_h / img.height
    w, h = int(img.width * ratio), int(img.height * ratio)
    img = img.resize((w, h), Image.Resampling.LANCZOS)
    pad = 14
    frame = Image.new("RGB", (w + pad * 2, h + pad * 2 + 28), "#1A1A1A")
    draw = ImageDraw.Draw(frame)
    # notch bar
    draw.rounded_rectangle([pad - 4, 8, w + pad + 4, h + pad + 20], radius=28, fill="#111")
    frame.paste(img, (pad, pad + 10))
    return frame


def make_figma_board():
    """Collage 6 UI screens — portrait board + 16:9 slide board."""
    files = [
        ("Login", "ui_login.png"),
        ("Register", "ui_register.png"),
        ("Camera", "ui_camera.png"),
        ("Diagnosis", "ui_diagnosis.png"),
        ("History", "ui_history.png"),
        ("Statistics", "ui_statistics.png"),
    ]

    def build(max_h, out_name, canvas_16x9=False):
        phones = []
        for label, name in files:
            im = Image.open(UI / name).convert("RGB")
            phones.append((label, phone_frame(im, max_h=max_h)))
        gap_x, gap_y = 28, 40
        col_w = max(p.width for _, p in phones) + gap_x
        row_h = max(p.height for _, p in phones) + 44
        grid_w = 3 * col_w - gap_x
        grid_h = 2 * row_h
        if canvas_16x9:
            W, H = 1920, 1080
            board = Image.new("RGB", (W, H), "#F0F2F5")
            draw = ImageDraw.Draw(board)
            draw.rectangle([0, 0, W, 64], fill="#2C2C2C")
            draw.text((28, 18), "DrPlant — UI / Figma Artboard", font=_font(28, True), fill="white")
            draw.text((W - 340, 24), "6 screens · paste-ready", font=_font(16), fill="#CCCCCC")
            ox = (W - grid_w) // 2
            oy = 64 + (H - 64 - grid_h) // 2
        else:
            mx, my = 48, 100
            W = mx * 2 + grid_w
            H = my + 16 + grid_h
            board = Image.new("RGB", (W, H), "#F0F2F5")
            draw = ImageDraw.Draw(board)
            draw.rectangle([0, 0, W, 72], fill="#2C2C2C")
            draw.text((28, 22), "DrPlant — UI Kit / Mobile Screens", font=_font(26, True), fill="white")
            ox, oy = mx, my
        for i, (label, phone) in enumerate(phones):
            r, c = i // 3, i % 3
            x = ox + c * col_w
            y = oy + r * row_h
            board.paste(phone, (x, y))
            tw = draw.textlength(label, font=_font(17, True))
            draw.text((x + (phone.width - tw) / 2, y + phone.height + 6),
                      label, font=_font(17, True), fill="#222")
        path = FIG / out_name
        board.save(path, "PNG", optimize=True)
        print("Figma board:", path, board.size)
        return path

    build(640, "slide_figma_ui_board.png", canvas_16x9=False)
    return build(420, "slide_figma_ui_board_16x9.png", canvas_16x9=True)


def set_run_font(run, size=12, bold=False, italic=False, color=None):
    run.font.name = "Times New Roman"
    rpr = run._element.get_or_add_rPr()
    rFonts = rpr.get_or_add_rFonts()
    rFonts.set(qn("w:ascii"), "Times New Roman")
    rFonts.set(qn("w:hAnsi"), "Times New Roman")
    rFonts.set(qn("w:eastAsia"), "Times New Roman")
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic
    if color is not None:
        run.font.color.rgb = color


def add_heading(doc, text, level=1):
    p = doc.add_paragraph()
    if level == 0:
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run(text)
        set_run_font(r, 16, True, color=RGBColor(0x1F, 0x4E, 0x79))
    else:
        r = p.add_run(text)
        set_run_font(r, 14 if level == 1 else 12, True, color=RGBColor(0x1F, 0x4E, 0x79))
    p.paragraph_format.space_before = Pt(10)
    p.paragraph_format.space_after = Pt(6)


def add_body(doc, text, bold=False, italic=False):
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.line_spacing = 1.25
    r = p.add_run(text)
    set_run_font(r, 12, bold, italic)


def add_bullets(doc, items):
    for item in items:
        p = doc.add_paragraph(style="List Bullet")
        p.paragraph_format.space_after = Pt(2)
        set_run_font(p.add_run(item), 12)


def add_speak(doc, text):
    add_body(doc, "Văn nói:", bold=True)
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.4)
    p.paragraph_format.space_after = Pt(8)
    p.paragraph_format.line_spacing = 1.3
    set_run_font(p.add_run(text), 12, False, True, RGBColor(0x33, 0x33, 0x33))


def add_note(doc, text):
    p = doc.add_paragraph()
    set_run_font(p.add_run("Ghi chú: "), 11, True, False, RGBColor(0x8B, 0x45, 0x00))
    set_run_font(p.add_run(text), 11, False, True, RGBColor(0x8B, 0x45, 0x00))


def hr(doc):
    p = doc.add_paragraph()
    set_run_font(p.add_run("─" * 48), 9, False, False, RGBColor(0x99, 0x99, 0x99))


def add_image(doc, path, width_in=6.2):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.add_run().add_picture(str(path), width=Inches(width_in))
    p.paragraph_format.space_after = Pt(6)


def make_doc(erd_path, figma_path):
    doc = Document()
    section = doc.sections[0]
    section.top_margin = Cm(2)
    section.bottom_margin = Cm(2)
    section.left_margin = Cm(2)
    section.right_margin = Cm(2)

    add_heading(doc, "NỘI DUNG SLIDE + VĂN NÓI THUYẾT TRÌNH", 0)
    add_heading(doc, "DrPlant – Ứng dụng Android hỗ trợ chẩn đoán bệnh lá cây", 0)
    add_body(doc, "Môn: Lập trình thiết bị di động  |  UEF  |  2026", italic=True)
    add_body(
        doc,
        "Ảnh ERD và board UI (kiểu Figma, bản 16:9) đã nhúng trong các slide tương ứng. "
        "Copy ảnh trong Word hoặc mở file PNG trong report_figures/slides/ để dán PowerPoint.",
    )
    add_note(
        doc,
        f"Paste-ready: {erd_path.name}  |  {figma_path.name}",
    )
    hr(doc)

    # SLIDE 1
    add_heading(doc, "SLIDE 1 – Trang bìa", 1)
    add_body(doc, "Nội dung slide:", bold=True)
    add_bullets(doc, [
        "Trường Đại học Kinh tế – Tài chính TP. Hồ Chí Minh (UEF)",
        "Đề tài: DrPlant – Ứng dụng Android hỗ trợ chẩn đoán bệnh lá cây",
        "Môn: Lập trình thiết bị di động",
        "GVHD: Trần Thị Hường",
        "Nhóm: Nguyễn Việt Anh, Nguyễn Thế Anh, Nguyễn Bảo Linh Nhi, Phan Thị Ngọc Duyên, Nguyễn Ngọc Phương Nghi",
        "TP. Hồ Chí Minh – 2026",
    ])
    add_speak(
        doc,
        "Xin chào thầy cô và các bạn. Nhóm em trình bày đề tài DrPlant – "
        "ứng dụng Android hỗ trợ chẩn đoán bệnh lá cây. "
        "Giảng viên hướng dẫn là cô Trần Thị Hường. "
        "Nhóm gồm năm thành viên; phần việc cụ thể sẽ thấy ngay ở bảng phân công.",
    )
    hr(doc)

    # SLIDE 2
    add_heading(doc, "SLIDE 2 – Mục lục", 1)
    add_body(doc, "Nội dung slide:", bold=True)
    add_bullets(doc, [
        "1. Thành viên nhóm & phân công",
        "2. Mô tả dự án & mục tiêu",
        "3. Sơ đồ ERD",
        "4. Thiết kế giao diện (UI / Figma board)",
        "5. Một số code chính",
        "6. Kiểm thử & test case",
        "7. Kết luận",
        "8. Thank you / Q&A",
    ])
    add_speak(
        doc,
        "Phần trình bày gồm tám mục: phân công nhóm, mô tả dự án, ERD, giao diện, "
        "code chính, kiểm thử, kết luận và phần hỏi đáp.",
    )
    hr(doc)

    # SLIDE 3 — member table like report
    add_heading(doc, "SLIDE 3 – Thành viên nhóm & phân công", 1)
    add_body(doc, "Nội dung slide: bảng thành viên (giống báo cáo tiến độ)", bold=True)

    table = doc.add_table(rows=6, cols=3)
    table.style = "Table Grid"
    headers = ["Member", "Student ID", "Main assignments (this milestone)"]
    rows = [
        ["Nguyễn Việt Anh", "235050236",
         "CameraActivity, gallery flow, diagnosis result screen, history helper, Gradle / Firebase / ONNX setup"],
        ["Nguyễn Thế Anh", "235053028",
         "Requirement notes, use-case wording, StatisticalActivity UI, navigation, string/UI resources"],
        ["Nguyễn Bảo Linh Nhi", "235057326",
         "UI review and testing on device, history filter flows, screenshot capture for report"],
        ["Phan Thị Ngọc Duyên", "235052977",
         "Login / Register activities and layouts, table content check, non-functional requirements"],
        ["Nguyễn Ngọc Phương Nghi", "235051516",
         "Permission / CameraX checklist, local storage notes, progress tracking against the plan"],
    ]
    for i, h in enumerate(headers):
        cell = table.rows[0].cells[i]
        cell.text = ""
        set_run_font(cell.paragraphs[0].add_run(h), 11, True)
    for r_i, row in enumerate(rows, start=1):
        for c_i, val in enumerate(row):
            table.rows[r_i].cells[c_i].text = ""
            set_run_font(table.rows[r_i].cells[c_i].paragraphs[0].add_run(val), 10)

    add_body(doc, "")
    add_note(doc, "Trên PowerPoint: copy nguyên bảng hoặc chụp bảng này dán vào 1 slide. Không cần 5 slide riêng.")
    add_speak(
        doc,
        "Nhóm có năm thành viên, phân việc theo bảng trên. "
        "Việt Anh lo camera, kết quả chẩn đoán và phần kỹ thuật tích hợp. "
        "Thế Anh phụ trách yêu cầu, use case và màn thống kê. "
        "Linh Nhi kiểm thử trên thiết bị và chụp minh họa. "
        "Ngọc Duyên làm đăng nhập–đăng ký. "
        "Phương Nghi theo dõi quyền, lưu trữ cục bộ và tiến độ so với kế hoạch. "
        "Nhờ chia việc rõ nên nhóm làm song song được các module chính.",
    )
    hr(doc)

    # SLIDE 4 project
    add_heading(doc, "SLIDE 4 – Mô tả dự án & mục tiêu", 1)
    add_body(doc, "Nội dung slide:", bold=True)
    add_bullets(doc, [
        "DrPlant: chụp / chọn ảnh lá → gợi ý chẩn đoán trên máy (sầu riêng, cà phê)",
        "Stack: Android Java, CameraX, Firebase Auth, ONNX Runtime",
        "Mục tiêu milestone: Auth + Camera/Gallery + Diagnosis + History + khung Statistics",
        "Chưa làm: sync server, CRUD khu vực/cây trồng đầy đủ",
    ])
    add_note(doc, "Có thể chèn thêm logo + 1 ảnh ui_camera cạnh bullet.")
    add_speak(
        doc,
        "DrPlant giúp người trồng chụp hoặc chọn ảnh lá cây và xem gợi ý ngay trên điện thoại. "
        "App dùng Firebase để đăng nhập và chạy mô hình ONNX trên máy, không bắt buộc gửi ảnh lên server. "
        "Milestone này hoàn thiện luồng chính; thống kê có dữ liệu thật và đồng bộ cloud sẽ làm tiếp.",
    )
    hr(doc)

    # SLIDE 5 ERD with image
    add_heading(doc, "SLIDE 5 – Sơ đồ ERD", 1)
    add_body(doc, "Nội dung slide: dán nguyên ảnh dưới đây (full slide hoặc 80% chiều ngang).", bold=True)
    add_image(doc, erd_path, 6.4)
    add_body(doc, "File gốc để paste:", bold=True)
    add_body(doc, str(erd_path), italic=True)
    add_bullets(doc, [
        "Xanh lá / xanh dương: đã có trong milestone (User Auth + History cục bộ)",
        "Cam: thực thể dự kiến cho thống kê & giai đoạn sau",
    ])
    add_speak(
        doc,
        "Đây là ERD định hướng của DrPlant. "
        "User do Firebase Auth quản lý. Mỗi lần chẩn đoán lưu thành DiagnosisHistoryItem trên máy, "
        "gắn với kết quả DiagnosisResponse. "
        "Các thực thể Area, Plant, DiseaseRecord vẽ màu cam vì chưa implement CRUD – "
        "nhóm giữ trong thiết kế để sau này thống kê và đồng bộ server có chỗ gắn vào.",
    )
    hr(doc)

    # SLIDE 6 Figma
    add_heading(doc, "SLIDE 6 – Thiết kế giao diện (UI / Figma board)", 1)
    add_body(
        doc,
        "Nội dung slide: dán board 6 màn hình bên dưới (artboard 1920×1080 sẵn cho PowerPoint).",
        bold=True,
    )
    add_image(doc, figma_path, 6.5)
    add_body(doc, "File gốc để paste (khuyến nghị):", bold=True)
    add_body(doc, str(figma_path), italic=True)
    add_bullets(doc, [
        "Login → Register → Camera → Diagnosis → History → Statistics",
        "Screenshot từ APK debug trên emulator (đúng UI đang ship)",
        "Bản dự phòng (dọc): report_figures/slides/slide_figma_ui_board.png",
    ])
    add_speak(
        doc,
        "Đây là bộ giao diện chính của app, nhóm gom như một artboard để nhìn tổng thể. "
        "Từ đăng nhập, đăng ký, sang camera là màn hình chính; sau đó là kết quả, lịch sử và thống kê. "
        "Ảnh lấy trực tiếp từ bản build đang chạy, nên slide và demo sẽ khớp nhau. "
        "Nếu thầy cô cần file Figma chi tiết, nhóm có thể bổ sung link sau.",
    )
    hr(doc)

    # SLIDE 7–9 code (shortened, keep scripts)
    add_heading(doc, "SLIDE 7 – Code: Lưu trữ cục bộ (thay Create DB)", 1)
    add_body(doc, "Nội dung slide:", bold=True)
    add_bullets(doc, [
        "DiagnosisHistoryManager + SharedPreferences + Gson",
        "Lưu List<DiagnosisHistoryItem> dạng JSON, tối đa ~100 mục",
    ])
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.4)
    set_run_font(
        p.add_run(
            "prefs.getString(\"history_items\", \"\");\n"
            "new Gson().fromJson(json, HISTORY_LIST_TYPE);\n"
            "saveHistoryItem(...); // prepend + trim 100"
        ),
        10,
    )
    add_speak(
        doc,
        "Milestone này chưa tạo SQL database. Nhóm lưu lịch sử bằng SharedPreferences và Gson, "
        "đủ dùng offline trên điện thoại; sau sẽ chuyển Room hoặc backend khi có Area và Plant.",
    )
    hr(doc)

    add_heading(doc, "SLIDE 8 – Code: Authentication", 1)
    add_body(doc, "Nội dung slide:", bold=True)
    add_bullets(doc, [
        "FirebaseAuth.signInWithEmailAndPassword",
        "Validate email/password trước khi gọi",
        "Thành công → CameraActivity; có reset password email",
    ])
    p = doc.add_paragraph()
    p.paragraph_format.left_indent = Cm(0.4)
    set_run_font(
        p.add_run(
            "firebaseAuth.signInWithEmailAndPassword(email, password)\n"
            "  .addOnCompleteListener(this, task -> {\n"
            "      if (task.isSuccessful()) goToCamera();\n"
            "      else showLoginError(task.getException());\n"
            "  });"
        ),
        10,
    )
    add_speak(
        doc,
        "Đăng nhập kiểm tra định dạng email rồi gọi Firebase. "
        "Đúng thì vào camera; sai thì báo lỗi. Người dùng cũng xin được email đặt lại mật khẩu.",
    )
    hr(doc)

    add_heading(doc, "SLIDE 9 – Code: Security", 1)
    add_body(doc, "Nội dung slide:", bold=True)
    add_bullets(doc, [
        "Không lưu mật khẩu plain text trên máy – giao Firebase Auth",
        "Xin quyền CAMERA / READ_MEDIA_IMAGES lúc chạy",
        "Activity nội bộ: android:exported=\"false\"",
        "Validate input; giữ google-services & SHA trong nhóm",
    ])
    add_speak(
        doc,
        "Bảo mật ở mức học phần: mật khẩu do Firebase giữ, quyền camera xin lúc chạy, "
        "Activity nội bộ không export, cấu hình Firebase không đưa công khai trên slide.",
    )
    hr(doc)

    # SLIDE 10 test
    add_heading(doc, "SLIDE 10 – Test case", 1)
    t2 = doc.add_table(rows=7, cols=4)
    t2.style = "Table Grid"
    for i, h in enumerate(["Mã", "Tình huống", "Kỳ vọng", "KQ"]):
        t2.rows[0].cells[i].text = ""
        set_run_font(t2.rows[0].cells[i].paragraphs[0].add_run(h), 10, True)
    cases = [
        ["TC01", "Sai mật khẩu", "Báo lỗi, không vào Camera", "Pass"],
        ["TC02", "Email sai format", "Lỗi trên ô Email", "Pass"],
        ["TC03", "Từ chối quyền Camera", "Không mở preview", "Pass"],
        ["TC04", "Chụp/chọn ảnh → Diagnosis", "Mở màn kết quả", "Pass"],
        ["TC05", "Lọc History (cà phê)", "Chỉ mục cà phê", "Pass"],
        ["TC06", "Statistics chưa có data", "Placeholder “—”", "Pass*"],
    ]
    for r_i, row in enumerate(cases, start=1):
        for c_i, val in enumerate(row):
            t2.rows[r_i].cells[c_i].text = ""
            set_run_font(t2.rows[r_i].cells[c_i].paragraphs[0].add_run(val), 10)
    add_body(doc, "")
    add_speak(
        doc,
        "Nhóm test các tình huống hay gặp: sai mật khẩu, email sai, từ chối quyền camera, "
        "luồng chụp ảnh sang kết quả, lọc lịch sử. "
        "Màn thống kê để trống số liệu là đúng với tiến độ hiện tại.",
    )
    hr(doc)

    # SLIDE 11 conclusion
    add_heading(doc, "SLIDE 11 – Kết luận", 1)
    add_bullets(doc, [
        "Hoàn thành: Auth, Camera/Gallery, Diagnosis, History, khung Statistics",
        "Tiếp theo: dữ liệu Area/Plant, sync server, cải thiện mô hình ONNX",
    ])
    add_speak(
        doc,
        "Tóm lại nhóm đã có ứng dụng Android chạy được luồng chính. "
        "Phần còn lại là gắn dữ liệu thật cho thống kê và hướng tới đồng bộ server.",
    )
    hr(doc)

    # SLIDE 12 thank you
    add_heading(doc, "SLIDE 12 – Thank you", 1)
    add_bullets(doc, [
        "Thank you – Xin cảm ơn thầy cô và các bạn",
        "Q&A",
    ])
    add_speak(
        doc,
        "Xin cảm ơn thầy cô và mọi người đã lắng nghe. Nhóm sẵn sàng nhận câu hỏi.",
    )
    hr(doc)

    add_heading(doc, "File ảnh sẵn sàng paste lên slide", 1)
    add_bullets(doc, [
        str(erd_path),
        str(figma_path),
        "Ảnh màn hình lẻ (nếu cần): " + str(UI),
    ])

    doc.save(str(OUT_DOC))
    doc.save(str(OUT_DOC.with_name("NoiDung_ThuyetTrinh_DrPlant_updated.docx")))
    print("Doc:", OUT_DOC)


def main():
    erd = make_erd()
    figma = make_figma_board()
    make_doc(erd, figma)


if __name__ == "__main__":
    main()

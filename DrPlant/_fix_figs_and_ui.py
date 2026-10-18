# -*- coding: utf-8 -*-
"""Fix diagrams, renumber figures, insert UI screenshots into the report."""
from pathlib import Path
import re
import subprocess
import time

import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Rectangle
from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Inches, Pt
from docx.text.paragraph import Paragraph

ROOT = Path(r"C:\AI-Agriculture-Ecosystem\DrPlant")
DOC_MAIN = ROOT / "BaoCaoTienDo_DrPlant_Mobile.docx"
DOC_ALT = ROOT / "BaoCaoTienDo_DrPlant_Mobile_updated.docx"
FIG_DIR = ROOT / "report_figures"
UI_DIR = FIG_DIR / "ui"
APK = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
PKG = "com.nckh.drplant"

FIG_DIR.mkdir(exist_ok=True)
UI_DIR.mkdir(exist_ok=True)

BOX_FACE = "#F7F7F7"
BOX_EDGE = "#222222"
ACCENT = "#1F4E79"
TEXT = "#111111"


def set_run_font(run, size=12, bold=False, italic=False):
    run.font.name = "Times New Roman"
    run._element.rPr.rFonts.set(qn("w:eastAsia"), "Times New Roman")
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic


def clear_num_pr(p):
    p_pr = p._p.get_or_add_pPr()
    num_pr = p_pr.find(qn("w:numPr"))
    if num_pr is not None:
        p_pr.remove(num_pr)


def rounded_box(ax, xy, w, h, text, fontsize=9, face=BOX_FACE):
    x, y = xy
    ax.add_patch(FancyBboxPatch(
        (x, y), w, h,
        boxstyle="round,pad=0.02,rounding_size=0.08",
        linewidth=1.15, edgecolor=BOX_EDGE, facecolor=face, zorder=2,
    ))
    ax.text(
        x + w / 2, y + h / 2, text,
        ha="center", va="center", fontsize=fontsize, color=TEXT,
        linespacing=1.35, zorder=3, clip_on=False,
    )


def arrow(ax, p1, p2, rad=0):
    ax.annotate(
        "", xy=p2, xytext=p1,
        arrowprops=dict(
            arrowstyle="-|>", color=BOX_EDGE, lw=1.05,
            shrinkA=2, shrinkB=2,
            connectionstyle=f"arc3,rad={rad}" if rad else "arc3,rad=0",
        ),
        zorder=1,
    )


def save_fig(fig, name):
    path = FIG_DIR / name
    fig.savefig(path, dpi=220, bbox_inches="tight", facecolor="white", pad_inches=0.25)
    plt.close(fig)
    return path


def diagram_architecture():
    """Layer titles sit in a dedicated header band; boxes never touch titles."""
    fig, ax = plt.subplots(figsize=(11.2, 8.6))
    ax.set_xlim(0, 11.2)
    ax.set_ylim(0, 11.0)
    ax.axis("off")
    ax.set_title(
        "DrPlant — Layered Application Architecture",
        fontsize=13, pad=16, color=ACCENT, weight="bold",
    )

    # y, h, title, color, component boxes (x, y, w, h, label)
    bands = [
        (7.55, 2.7, "1) Presentation layer — Activities / XML layouts", "#E8F0F8", [
            (0.55, 7.75, 1.85, 1.45, "Login\nRegister"),
            (2.65, 7.75, 1.85, 1.45, "Camera\nActivity"),
            (4.75, 7.75, 1.95, 1.45, "Diagnosis\nActivity"),
            (6.95, 7.75, 1.65, 1.45, "History"),
            (8.85, 7.75, 1.65, 1.45, "Stats\nUI"),
        ]),
        (4.15, 2.7, "2) Application helpers — Utils / Models", "#EEF6EE", [
            (0.7, 4.35, 2.9, 1.45, "DiagnosisHelper\nIntent packing"),
            (3.95, 4.35, 3.1, 1.45, "DiagnosisHistoryManager\nSharedPreferences + Gson"),
            (7.4, 4.35, 2.85, 1.45, "OnnxDiagnosisMapper\nResult mapping"),
        ]),
        (0.75, 2.7, "3) Platform services & on-device engine", "#F8F0E8", [
            (0.7, 0.95, 2.9, 1.45, "Firebase Auth\n(email / password)"),
            (3.95, 0.95, 3.1, 1.45, "CameraX + Gallery\n(permissions)"),
            (7.4, 0.95, 2.85, 1.45, "ONNX Runtime\nbest.onnx asset"),
        ]),
    ]

    for y, h, title, color, boxes in bands:
        ax.add_patch(FancyBboxPatch(
            (0.3, y), 10.6, h,
            boxstyle="round,pad=0.02,rounding_size=0.06",
            linewidth=1.0, edgecolor="#666666", facecolor=color, alpha=0.72, zorder=0,
        ))
        # Dedicated header strip — title fully inside, clear of component boxes
        ax.add_patch(Rectangle(
            (0.42, y + h - 0.55), 10.36, 0.42,
            linewidth=0, facecolor="white", alpha=0.88, zorder=1,
        ))
        ax.text(
            0.55, y + h - 0.34, title,
            fontsize=10, color=ACCENT, ha="left", va="center", weight="bold", zorder=2,
        )
        for bx, by, bw, bh, label in boxes:
            rounded_box(ax, (bx, by), bw, bh, label, fontsize=9)

    for x in (1.6, 5.55, 8.7):
        arrow(ax, (x, 7.75), (x, 5.8))
        arrow(ax, (x, 4.35), (x, 2.4))

    ax.text(
        5.6, 0.28,
        "Internet is used for Firebase Authentication only in this milestone.\n"
        "History stays on-device; statistics UI has no remote database yet.",
        ha="center", va="center", fontsize=8.5, color="#444444", linespacing=1.35,
    )
    return save_fig(fig, "fig_3_4_architecture.png")


def diagram_navigation():
    """Generous gaps; labels only in open space, never on box borders."""
    fig, ax = plt.subplots(figsize=(11.4, 7.6))
    ax.set_xlim(-0.8, 11.5)
    ax.set_ylim(-0.2, 9.0)
    ax.axis("off")
    ax.set_title(
        "DrPlant — Screen Navigation Flow",
        fontsize=13, pad=14, color=ACCENT, weight="bold",
    )

    nodes = {
        "login": (0.5, 6.9, 2.4, 1.35, "LoginActivity\n(launcher)"),
        "reg": (0.5, 3.9, 2.4, 1.35, "RegisterActivity"),
        "cam": (4.4, 6.9, 2.5, 1.35, "CameraActivity\n(home)"),
        "diag": (8.3, 6.9, 2.4, 1.35, "DiagnosisActivity"),
        "hist": (4.4, 3.7, 2.5, 1.35, "HistoryActivity"),
        "stat": (4.4, 0.7, 2.5, 1.35, "StatisticalActivity"),
    }
    for key, (x, y, w, h, label) in nodes.items():
        face = "#D6E8F7" if key == "cam" else BOX_FACE
        rounded_box(ax, (x, y), w, h, label, fontsize=9.5, face=face)

    # login -> camera (label above)
    arrow(ax, (2.9, 7.55), (4.4, 7.55))
    ax.text(3.65, 8.15, "sign-in OK /\nalready signed in",
            ha="center", va="bottom", fontsize=7.8, color="#333", linespacing=1.2)

    # camera -> diagnosis
    arrow(ax, (6.9, 7.55), (8.3, 7.55))
    ax.text(7.6, 8.15, "capture /\ngallery",
            ha="center", va="bottom", fontsize=7.8, color="#333", linespacing=1.2)

    # camera -> login logout (lower path, label below)
    arrow(ax, (4.4, 7.2), (2.9, 7.2))
    ax.text(3.65, 6.55, "logout", ha="center", va="top", fontsize=7.8, color="#333")

    # login <-> register (labels outside the column)
    arrow(ax, (1.15, 6.9), (1.15, 5.25))
    ax.text(0.35, 6.05, "register", ha="right", va="center", fontsize=7.8, color="#333")
    arrow(ax, (2.25, 5.25), (2.25, 6.9))
    ax.text(2.5, 6.05, "back", ha="left", va="center", fontsize=7.8, color="#333")

    # camera -> history
    arrow(ax, (5.65, 6.9), (5.65, 5.05))
    ax.text(5.95, 5.95, "history", ha="left", va="center", fontsize=7.8, color="#333")

    # history -> stats
    arrow(ax, (5.65, 3.7), (5.65, 2.05))
    ax.text(5.95, 2.85, "stats", ha="left", va="center", fontsize=7.8, color="#333")

    # stats -> camera home: left, then ABOVE login, then down into Camera
    arrow(ax, (4.4, 1.35), (-0.35, 1.35))
    arrow(ax, (-0.35, 1.35), (-0.35, 8.65))
    arrow(ax, (-0.35, 8.65), (5.65, 8.65))
    arrow(ax, (5.65, 8.65), (5.65, 8.25))
    ax.text(-0.55, 4.4, "nav\nHome", ha="right", va="center",
            fontsize=7.8, color="#333", linespacing=1.2)

    # Note box — right side, clear of arrows
    ax.add_patch(FancyBboxPatch(
        (8.15, 3.5), 2.75, 1.7,
        boxstyle="round,pad=0.03,rounding_size=0.06",
        linewidth=0.9, edgecolor="#AAAAAA", facecolor="#FAFAFA", zorder=2,
    ))
    ax.text(
        9.52, 4.35,
        "Diagnosis can also\nopen from a history\nrow (no re-save).",
        ha="center", va="center", fontsize=8, color="#444", linespacing=1.35,
    )
    return save_fig(fig, "fig_3_2_navigation.png")


def replace_image_by_caption_keyword(doc, keyword, new_image, width_in=5.9):
    paras = list(doc.paragraphs)
    for i, p in enumerate(paras):
        if not (p.style and p.style.name == "Caption"):
            continue
        if keyword.lower() not in p.text.lower():
            continue
        if i == 0:
            return False
        prev = paras[i - 1]
        for child in list(prev._p):
            prev._p.remove(child)
        run = prev.add_run()
        run.add_picture(str(new_image), width=Inches(width_in))
        prev.alignment = WD_ALIGN_PARAGRAPH.CENTER
        return True
    return False


def set_caption_text(paragraph, kind, number, title):
    for child in list(paragraph._p):
        if child.tag.endswith("}r") or child.tag.endswith("}hyperlink"):
            paragraph._p.remove(child)
    text = f"{kind} {number}. {title}"
    run = paragraph.add_run(text)
    set_run_font(run, 12, False, True)
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    clear_num_pr(paragraph)


def renumber_captions(doc):
    table_i = 1
    design_order = [
        ("data flow from image", "3.1", "Data flow from image capture to result and local history"),
        ("navigation flow", "3.2", "Navigation flow between the main DrPlant screens"),
        ("use case diagram", "3.3", "Use case diagram for the progress build"),
        ("layered architecture", "3.4", "Layered architecture of the DrPlant Android application"),
    ]
    ui_order = [
        ("login screen", "4.1", "Login screen of DrPlant"),
        ("register screen", "4.2", "Register screen of DrPlant"),
        ("camera home screen", "4.3", "Camera home screen of DrPlant"),
        ("camera screen", "4.3", "Camera home screen of DrPlant"),
        ("diagnosis result screen", "4.4", "Diagnosis result screen of DrPlant"),
        ("diagnosis screen", "4.4", "Diagnosis result screen of DrPlant"),
        ("history screen", "4.5", "History screen of DrPlant"),
        ("statistics screen", "4.6", "Statistics screen of DrPlant"),
    ]

    for p in doc.paragraphs:
        if not (p.style and p.style.name == "Caption"):
            continue
        raw = p.text.strip()
        t = raw.lower()
        if t.startswith("table") and "figure" not in t:
            title = re.sub(r"^(Table|Figure)\s*[\d.]+\s*", "", raw, flags=re.I).strip(" .")
            set_caption_text(p, "Table", str(table_i), title)
            table_i += 1

    for p in doc.paragraphs:
        if not (p.style and p.style.name == "Caption"):
            continue
        t = p.text.lower()
        for key, num, title in design_order + ui_order:
            if key in t:
                set_caption_text(p, "Figure", num, title)
                break


def find_heading(doc, text):
    for p in doc.paragraphs:
        if p.text.strip() == text:
            return p
    return None


def _next_p(p):
    elm = p._p.getnext()
    while elm is not None:
        if elm.tag == qn("w:p"):
            return Paragraph(elm, p._parent)
        elm = elm.getnext()
    return None


def has_caption_body(doc, keyword):
    """True only if a Caption-styled paragraph in the body matches (not LOF)."""
    for p in doc.paragraphs:
        if p.style and p.style.name == "Caption" and keyword.lower() in p.text.lower():
            return True
    return False


def remove_existing_ui_block(doc):
    """Remove previously inserted UI intro/images/captions under 4.1 if present."""
    h = find_heading(doc, "4.1. What Was Built")
    if h is None:
        return
    nxt = _next_p(h)
    to_remove = []
    while nxt is not None:
        style = nxt.style.name if nxt.style else ""
        if style.startswith("Heading"):
            break
        t = nxt.text.strip().lower()
        xml = nxt._p.xml
        is_ui_cap = style == "Caption" and ("screen of drplant" in t or "login screen" in t)
        is_ui_intro = "following figures were captured" in t
        is_ui_img = ("drawing" in xml or "pic:pic" in xml) and not t.startswith("table")
        # Only strip UI-related nodes after the table caption / body text of 4.1
        if is_ui_cap or is_ui_intro:
            to_remove.append(nxt)
        elif is_ui_img:
            # image para just before a UI caption — mark when next is UI caption
            peek = _next_p(nxt)
            if peek and peek.style and peek.style.name == "Caption":
                pt = peek.text.lower()
                if "screen of drplant" in pt:
                    to_remove.append(nxt)
        nxt = _next_p(nxt)

    for p in to_remove:
        p._p.getparent().remove(p._p)


def insert_ui_block(doc, heading_text, images_with_captions):
    h = find_heading(doc, heading_text)
    if h is None:
        raise RuntimeError("heading missing: " + heading_text)
    anchor = h
    nxt = _next_p(h)
    while nxt is not None:
        style = nxt.style.name if nxt.style else ""
        if style.startswith("Heading"):
            break
        if nxt.text.strip() or ("drawing" in nxt._p.xml):
            anchor = nxt
        nxt = _next_p(nxt)

    p_intro_elm = OxmlElement("w:p")
    anchor._p.addnext(p_intro_elm)
    p_intro = Paragraph(p_intro_elm, anchor._parent)
    clear_num_pr(p_intro)
    p_intro.paragraph_format.first_line_indent = Cm(1.27)
    p_intro.paragraph_format.line_spacing = 1.5
    set_run_font(
        p_intro.add_run(
            "The following figures were captured from the running DrPlant debug build "
            "on an Android emulator (Medium Phone). They show the main screens of the "
            "progress milestone."
        ),
        13, False,
    )
    anchor = p_intro

    for img_path, fig_num, title in images_with_captions:
        if not Path(img_path).exists():
            continue
        p_img_elm = OxmlElement("w:p")
        anchor._p.addnext(p_img_elm)
        p_img = Paragraph(p_img_elm, anchor._parent)
        clear_num_pr(p_img)
        p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_img.paragraph_format.first_line_indent = Cm(0)
        p_img.paragraph_format.space_before = Pt(8)
        run = p_img.add_run()
        run.add_picture(str(img_path), width=Inches(2.55))
        p_cap_elm = OxmlElement("w:p")
        p_img._p.addnext(p_cap_elm)
        p_cap = Paragraph(p_cap_elm, anchor._parent)
        p_cap.style = "Caption"
        clear_num_pr(p_cap)
        set_caption_text(p_cap, "Figure", fig_num, title)
        anchor = p_cap


def update_list_of_figures_static(doc):
    h = find_heading(doc, "LIST OF FIGURES")
    if h is None:
        return
    nxt = h._p.getnext()
    while nxt is not None:
        if nxt.tag != qn("w:p"):
            break
        p = Paragraph(nxt, h._parent)
        if p.style and p.style.name == "Heading 1":
            break
        following = nxt.getnext()
        nxt.getparent().remove(nxt)
        nxt = following

    lines = [
        "Figure 3.1. Data flow from image capture to result and local history",
        "Figure 3.2. Navigation flow between the main DrPlant screens",
        "Figure 3.3. Use case diagram for the progress build",
        "Figure 3.4. Layered architecture of the DrPlant Android application",
        "Figure 4.1. Login screen of DrPlant",
        "Figure 4.2. Register screen of DrPlant",
        "Figure 4.3. Camera home screen of DrPlant",
        "Figure 4.4. Diagnosis result screen of DrPlant",
        "Figure 4.5. History screen of DrPlant",
        "Figure 4.6. Statistics screen of DrPlant",
    ]
    anchor = h
    for line in lines:
        elm = OxmlElement("w:p")
        anchor._p.addnext(elm)
        p = Paragraph(elm, h._parent)
        clear_num_pr(p)
        p.paragraph_format.first_line_indent = Cm(0)
        p.paragraph_format.space_after = Pt(2)
        p.paragraph_format.line_spacing = 1.15
        set_run_font(p.add_run(line), 13, False)
        anchor = p


def adb(*args, check=True):
    return subprocess.run(["adb", *args], capture_output=True, text=True, check=check)


def screencap(local_path: Path):
    remote = "/sdcard/Download/_drplant_cap.png"
    adb("shell", "screencap", "-p", remote)
    adb("pull", remote, str(local_path))
    adb("shell", "rm", "-f", remote, check=False)


def set_activities_exported(exported: bool):
    text = MANIFEST.read_text(encoding="utf-8")
    # Force exported flag on all activity tags that have android:exported
    flag = "true" if exported else "false"
    # Keep LoginActivity launcher as true always; for capture set all true
    new = re.sub(
        r'(android:name="\.(?:Login|Register|Camera|Diagnosis|History|Statistical)Activity"\s*\n\s*android:exported=")(?:true|false)(")',
        rf"\1{flag}\2",
        text,
    )
    # Login must stay exported for launcher
    new = re.sub(
        r'(android:name="\.LoginActivity"\s*\n\s*android:exported=")(?:true|false)(")',
        r'\1true\2',
        new,
    )
    MANIFEST.write_text(new, encoding="utf-8")


def rebuild_and_install():
    gradlew = ROOT / "gradlew.bat"
    subprocess.run(
        [str(gradlew), ":app:assembleDebug", "-q"],
        cwd=str(ROOT), check=True,
    )
    adb("install", "-r", str(APK))


def capture_ui_screens():
    """Launch each activity and save screenshots."""
    set_activities_exported(True)
    try:
        rebuild_and_install()
        screens = [
            ("ui_login.png", f"{PKG}/.LoginActivity"),
            ("ui_register.png", f"{PKG}/.RegisterActivity"),
            ("ui_camera.png", f"{PKG}/.CameraActivity"),
            ("ui_diagnosis.png", f"{PKG}/.DiagnosisActivity"),
            ("ui_history.png", f"{PKG}/.HistoryActivity"),
            ("ui_statistics.png", f"{PKG}/.StatisticalActivity"),
        ]
        for name, component in screens:
            adb("shell", "am", "force-stop", PKG, check=False)
            time.sleep(0.4)
            adb("shell", "am", "start", "-n", component)
            time.sleep(1.8)
            screencap(UI_DIR / name)
            print("Captured", name)
    finally:
        set_activities_exported(False)
        # restore Login exported=true, others false (default project state)
        text = MANIFEST.read_text(encoding="utf-8")
        # Diagnosis was originally exported=true in this project
        text = re.sub(
            r'(android:name="\.DiagnosisActivity"\s*\n\s*android:exported=")(?:true|false)(")',
            r'\1true\2',
            text,
        )
        MANIFEST.write_text(text, encoding="utf-8")


def open_doc():
    # Prefer updated if main is typically locked; try main first at save time
    if DOC_ALT.exists():
        return Document(str(DOC_ALT)), DOC_ALT
    return Document(str(DOC_MAIN)), DOC_MAIN


def main(capture=True, ui_only=False):
    if capture:
        print("Capturing UI from emulator...")
        capture_ui_screens()

    if not ui_only:
        fig_arch = diagram_architecture()
        fig_nav = diagram_navigation()
        print("Rewrote", fig_arch.name, fig_nav.name)

    doc, src = open_doc()
    print("Opened", src.name)

    if not ui_only:
        ok1 = replace_image_by_caption_keyword(
            doc, "layered architecture", FIG_DIR / "fig_3_4_architecture.png", 5.9
        )
        ok2 = replace_image_by_caption_keyword(
            doc, "navigation flow", FIG_DIR / "fig_3_2_navigation.png", 5.9
        )
        print("Replaced images:", ok1, ok2)

    ui_files = [
        (UI_DIR / "ui_login.png", "4.1", "Login screen of DrPlant"),
        (UI_DIR / "ui_register.png", "4.2", "Register screen of DrPlant"),
        (UI_DIR / "ui_camera.png", "4.3", "Camera home screen of DrPlant"),
        (UI_DIR / "ui_diagnosis.png", "4.4", "Diagnosis result screen of DrPlant"),
        (UI_DIR / "ui_history.png", "4.5", "History screen of DrPlant"),
        (UI_DIR / "ui_statistics.png", "4.6", "Statistics screen of DrPlant"),
    ]
    existing_ui = [(p, n, t) for p, n, t in ui_files if p.exists()]

    if existing_ui:
        if has_caption_body(doc, "Login screen of DrPlant"):
            remove_existing_ui_block(doc)
            print("Removed previous UI block")
        insert_ui_block(doc, "4.1. What Was Built", existing_ui)
        print("Inserted UI screenshots:", len(existing_ui))

    renumber_captions(doc)
    update_list_of_figures_static(doc)

    for target in (DOC_MAIN, DOC_ALT):
        try:
            doc.save(str(target))
            print("Saved", target)
            break
        except PermissionError:
            print("Locked:", target.name)
    else:
        raise RuntimeError("Could not save any document copy")


if __name__ == "__main__":
    import sys
    args = set(sys.argv[1:])
    main(capture=("nocap" not in args), ui_only=("ui" in args))

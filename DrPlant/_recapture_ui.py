# -*- coding: utf-8 -*-
import re
import subprocess
import sys
import time
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

ROOT = Path(r"C:\AI-Agriculture-Ecosystem\DrPlant")
UI_DIR = ROOT / "report_figures" / "ui"
MANIFEST = ROOT / "app" / "src" / "main" / "AndroidManifest.xml"
APK = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
PKG = "com.nckh.drplant"


def adb(*args, check=True):
    r = subprocess.run(["adb", *args], capture_output=True, text=True)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)}\n{r.stderr}\n{r.stdout}")
    return r


def ensure_exported_for_capture():
    text = MANIFEST.read_text(encoding="utf-8")
    for name in ("RegisterActivity", "CameraActivity", "HistoryActivity", "StatisticalActivity"):
        text = re.sub(
            rf'(android:name="\.{name}"\s*\n\s*android:exported=")(?:true|false)(")',
            r"\1true\2",
            text,
        )
    MANIFEST.write_text(text, encoding="utf-8")


def restore_manifest():
    text = MANIFEST.read_text(encoding="utf-8")
    for name in ("RegisterActivity", "CameraActivity", "HistoryActivity", "StatisticalActivity"):
        text = re.sub(
            rf'(android:name="\.{name}"\s*\n\s*android:exported=")(?:true|false)(")',
            r"\1false\2",
            text,
        )
    text = re.sub(
        r'(android:name="\.DiagnosisActivity"\s*\n\s*android:exported=")(?:true|false)(")',
        r"\1true\2",
        text,
    )
    MANIFEST.write_text(text, encoding="utf-8")


def screencap(path: Path):
    remote = "/sdcard/Download/_drplant_cap.png"
    adb("shell", "screencap", "-p", remote)
    adb("pull", remote, str(path))
    adb("shell", "rm", "-f", remote, check=False)


def dump_xml() -> str:
    remote = "/sdcard/Download/ui.xml"
    adb("shell", "uiautomator", "dump", remote, check=False)
    local = UI_DIR / "_ui_dump.xml"
    adb("pull", remote, str(local), check=False)
    if local.exists():
        return local.read_text(encoding="utf-8", errors="ignore")
    return ""


def dismiss_compat_dialog():
    """Dismiss Android 16KB page-size compatibility warning if shown."""
    for _ in range(4):
        xml = dump_xml()
        if "16 KB" not in xml and "16KB" not in xml and "App Compatibility" not in xml:
            return
        # Prefer Don't Show Again
        m = re.search(
            r'text="Don.?t Show Again"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
            xml,
        )
        if not m:
            m = re.search(
                r'text="OK"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
                xml,
            )
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            cx, cy = (x1 + x2) // 2, (y1 + y2) // 2
            adb("shell", "input", "tap", str(cx), str(cy), check=False)
            time.sleep(0.8)
            print(f"Dismissed dialog at ({cx},{cy})")
        else:
            adb("shell", "input", "keyevent", "4", check=False)  # BACK
            time.sleep(0.5)


def dump_texts(label: str):
    xml = dump_xml()
    texts = [t for t in re.findall(r'text="([^"]+)"', xml) if t.strip()]
    print(label, "texts:", texts[:20])
    return texts


def main():
    UI_DIR.mkdir(parents=True, exist_ok=True)

    ensure_exported_for_capture()
    print("Manifest set exported=true")
    subprocess.run(
        [str(ROOT / "gradlew.bat"), ":app:assembleDebug", "-q"],
        cwd=str(ROOT), check=True,
    )
    adb("install", "-r", str(APK))
    for perm in (
        "android.permission.CAMERA",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_EXTERNAL_STORAGE",
    ):
        adb("shell", "pm", "grant", PKG, perm, check=False)

    # Warm start + dismiss dialog once (do not pm clear — keeps "Don't Show Again")
    adb("shell", "am", "start", "-n", f"{PKG}/.LoginActivity")
    time.sleep(2.0)
    dismiss_compat_dialog()
    time.sleep(0.5)

    screens = [
        ("ui_login.png", f"{PKG}/.LoginActivity", 2.2),
        ("ui_register.png", f"{PKG}/.RegisterActivity", 2.2),
        ("ui_camera.png", f"{PKG}/.CameraActivity", 3.2),
        ("ui_diagnosis.png", f"{PKG}/.DiagnosisActivity", 2.2),
        ("ui_history.png", f"{PKG}/.HistoryActivity", 2.2),
        ("ui_statistics.png", f"{PKG}/.StatisticalActivity", 2.2),
    ]

    try:
        for name, component, wait in screens:
            # Do NOT force-stop — that can re-trigger the dialog
            adb("shell", "am", "start", "-W", "-n", component)
            time.sleep(wait)
            dismiss_compat_dialog()
            time.sleep(0.4)
            path = UI_DIR / name
            screencap(path)
            print("saved", path.name, path.stat().st_size)
            dump_texts(name.replace(".png", ""))
    finally:
        restore_manifest()
        print("Manifest restored")


if __name__ == "__main__":
    main()

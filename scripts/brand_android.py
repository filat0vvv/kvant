#!/usr/bin/env python3
"""Apply kvantGram branding to the pinned Telegram-FOSS checkout."""
import argparse
import os
from pathlib import Path
import re
import shutil
from xml.sax.saxutils import escape

from PIL import Image

REPO = Path(__file__).resolve().parents[1]
ANDROID_NS = "http://schemas.android.com/apk/res/android"


def replace_required(path, pattern, replacement, *, count=0):
    text = path.read_text(encoding="utf-8")
    text, changes = re.subn(pattern, replacement, text, count=count)
    if not changes:
        raise RuntimeError(f"Expected upstream text missing in {path}")
    path.write_text(text, encoding="utf-8")


def apply(root, app_name, app_id):
    if not app_name.strip() or any(ord(c) < 32 for c in app_name):
        raise ValueError("App name must be non-empty and contain no control characters")
    if not re.fullmatch(r"[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*){2,}", app_id):
        raise ValueError("Invalid Android application ID")
    properties = root / "gradle.properties"
    replace_required(properties, r"(?m)^APP_PACKAGE=.*$", lambda _: f"APP_PACKAGE={app_id}")
    replace_required(properties, r"-XX:MaxPermSize=\S+", "")

    # Keep Java package names intact; APP_PACKAGE controls the install identity.
    for path in (root / "TMessagesProj/src/main/res").glob("values*/strings.xml"):
        text = path.read_text(encoding="utf-8")
        for name, value in (("AppName", app_name), ("AppNameBeta", app_name + " Beta")):
            # Android string resources require escaping apostrophes, quotes and backslashes.
            value = value.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
            value = escape(value).replace("@", "\\@").replace("?", "\\?")
            text = re.sub(rf'(<string name="{name}">).*?(</string>)',
                          lambda m: m[1] + value + m[2], text, flags=re.S)
        path.write_text(text, encoding="utf-8")
    manifests = list((root / "TMessagesProj/config").rglob("AndroidManifest*.xml"))
    for path in manifests:
        text = path.read_text(encoding="utf-8")
        text = re.sub(r'android:label="Telegram FOSS(?: Beta)?"',
                      'android:label="@string/AppName"', text)
        path.write_text(text, encoding="utf-8")

    # Remote language packs must not restore Telegram's AppName.
    build_vars = root / "TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java"
    replace_required(build_vars, r"USE_CLOUD_STRINGS = true", "USE_CLOUD_STRINGS = false")
    # The inherited updater targets Telegram-FOSS, not signed kvantGram builds.
    replace_required(build_vars, r"CHECK_UPDATES = true", "CHECK_UPDATES = false")

    library = root / "TMessagesProj/build.gradle"
    replace_required(library, r"(    defaultConfig \{)",
                     r'\1\n        ndk { abiFilters "arm64-v8a" }', count=1)
    app = root / "TMessagesProj_App/build.gradle"
    replace_required(app, r'abiFilters "armeabi-v7a", "arm64-v8a", "x86", "x86_64"',
                     'abiFilters "arm64-v8a"')
    with app.open("a", encoding="utf-8") as f:
        f.write('''
// kvantGram: reuse the owner's signing key for every release.
android {
    signingConfigs {
        kvantRelease {
            storeFile file(System.getenv('KVANT_KEYSTORE_PATH'))
            storePassword System.getenv('ANDROID_KEYSTORE_PASSWORD')
            keyAlias System.getenv('ANDROID_KEY_ALIAS')
            keyPassword System.getenv('ANDROID_KEY_PASSWORD')
        }
    }
    buildTypes.release.signingConfig = signingConfigs.kvantRelease
}
''')

    # Preserve the existing proxy implementation; no new proxy behavior is introduced here.
    java_dir = root / "TMessagesProj/src/main/java/org/telegram/messenger"
    shutil.copyfile(REPO / "assets/AutoProxyManager.java", java_dir / "AutoProxyManager.java")
    shared = java_dir / "SharedConfig.java"
    replace_required(shared, r"proxyListLoaded = true;",
                     "proxyListLoaded = true;\n            AutoProxyManager.start();", count=1)

    resources = root / "TMessagesProj/src/main/res"
    image = Image.open(REPO / "assets/kvantgram-logo.png").convert("RGB")
    # Standard density exports; Android handles adaptive circle/squircle masks.
    for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                          ("xxhdpi", 144), ("xxxhdpi", 192)):
        folder = resources / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        icon = image.resize((size, size), Image.Resampling.LANCZOS)
        for name in ("ic_launcher", "ic_launcher_round"):
            icon.save(folder / f"{name}.png")
    folder = resources / "drawable-nodpi"
    folder.mkdir(parents=True, exist_ok=True)
    image.resize((512, 512), Image.Resampling.LANCZOS).save(folder / "kvant_logo.png")
    drawable = resources / "drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    (drawable / "kvant_foreground.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="http://schemas.android.com/apk/res/android" android:inset="14%">
    <bitmap android:src="@drawable/kvant_logo" android:gravity="fill" />
</inset>
''', encoding="utf-8")
    adaptive = resources / "mipmap-anydpi-v26"
    adaptive.mkdir(parents=True, exist_ok=True)
    xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/kvant_icon_background" />
    <foreground android:drawable="@drawable/kvant_foreground" />
</adaptive-icon>
'''
    for name in ("ic_launcher", "ic_launcher_round"):
        (adaptive / f"{name}.xml").write_text(xml, encoding="utf-8")
    (resources / "values/kvant_brand.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<resources><color name="kvant_icon_background">#001139</color></resources>
''', encoding="utf-8")
    # Alternative launcher icons inherit the same mark, preserving alias component names.
    for path in resources.glob("mipmap-*/icon_*_launcher*.png"):
        density = path.parent.name.removeprefix("mipmap-")
        shutil.copyfile(resources / f"mipmap-{density}/ic_launcher.png", path)
    for path in adaptive.glob("icon_*_launcher*.xml"):
        path.write_text(xml, encoding="utf-8")
    print(f"Applied branding: {app_name} ({app_id}), arm64-v8a")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    args = parser.parse_args()
    apply(args.source, os.environ.get("APP_NAME", "kvantGram"),
          os.environ.get("APP_ID", "com.kvant.messenger"))

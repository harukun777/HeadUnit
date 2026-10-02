"""Apply Auto on Andoroid display names and Android identity without changing JNI class names."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
old = "com.andrerinas.headunitrevived"
new = "com.retportal.retdrive"

def write_changed(path, text):
    before = path.read_text(encoding="utf-8")
    if text != before:
        path.write_text(text, encoding="utf-8")

for path in (root / "app/src/main/res").rglob("*.xml"):
    text = path.read_text(encoding="utf-8")
    text = text.replace("Headunit Revived", "Auto on Andoroid").replace("HeadUnit Revived", "Auto on Andoroid")
    if path.name == "shortcuts.xml":
        text = text.replace(f'android:targetPackage="{old}"', f'android:targetPackage="{new}"')
        text = text.replace(f'android:action="{old}.', f'android:action="{new}.')
    write_changed(path, text)

manifest = root / "app/src/main/AndroidManifest.xml"
text = manifest.read_text(encoding="utf-8")
text = text.replace(f'{old}.permission.', f'{new}.permission.').replace(f'{old}.ACTION_', f'{new}.ACTION_')
write_changed(manifest, text)

for base in (root / "app/src/main/java", root / "contract/src/main/java"):
    for path in base.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        # Restrict substitutions to literal runtime IDs, preserving package/import/JNI names.
        text = re.sub(r'"' + re.escape(old) + r'(?=\.(?:[A-Z_]+|aap\.action\.|permission\.)|\")', '"' + new, text)
        text = text.replace('"Headunit Revived"', '"Auto on Andoroid"')
        text = text.replace('"HeadUnit-Revived/${BuildConfig.VERSION_NAME}"', '"Auto on Andoroid/${BuildConfig.VERSION_NAME}"')
        write_changed(path, text)

"""Copies the signed APK into release/, writes release/latest.json and resets the notes.

Usage: publish_release.py <versionCode> <versionName>
"""
import hashlib
import json
import pathlib
import re
import shutil
import sys

code, name = int(sys.argv[1]), sys.argv[2]
root = pathlib.Path(__file__).resolve().parents[2]
release = root / "release"
apk = root / "app/build/outputs/apk/release/app-release.apk"

# The stable download plus one numbered copy of the current version.
for old in release.glob("Plate-*.apk"):
    old.unlink()
shutil.copyfile(apk, release / "Plate.apk")
shutil.copyfile(apk, release / f"Plate-{name}.apk")

notes_file = release / "next-notes.md"
notes = [l[2:].strip() for l in notes_file.read_text(encoding="utf-8").splitlines() if l.startswith("- ") and l[2:].strip()]
latest = {
    "versionCode": code,
    "versionName": name,
    "notes": notes or ["Bug fixes and improvements"],
    "apk": "https://raw.githubusercontent.com/mrboora04/ICE3/main/release/Plate.apk",
    # Plate checks the downloaded update against this before it can be installed.
    "sha256": hashlib.sha256((release / "Plate.apk").read_bytes()).hexdigest(),
}
(release / "latest.json").write_text(json.dumps(latest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
notes_file.write_text(
    "# What's new in the next version\n\n"
    "Write two or three short lines, each starting with \"- \". They show in the app's Update card.\n",
    encoding="utf-8",
)

readme = root / "README.md"
text = readme.read_text(encoding="utf-8")
readme.write_text(re.sub(r"release/Plate-[0-9][0-9.]*\.apk", f"release/Plate-{name}.apk", text), encoding="utf-8")
print(f"Published Plate {name} ({code}) with {len(latest['notes'])} notes")

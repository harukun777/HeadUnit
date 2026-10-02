"""Generate an update feed from an APK; publish both files on an HTTPS server."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
from urllib.parse import urlparse

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("apk", type=Path)
parser.add_argument("apk_url")
parser.add_argument("--variant", default="android51")
parser.add_argument("--aapt", default="build/toolchain/sdk/build-tools/35.0.0/aapt.exe")
parser.add_argument("--output", type=Path, default=Path("update.json"))
args = parser.parse_args()
url = urlparse(args.apk_url)
if url.scheme != "https" or not url.hostname or url.username:
    parser.error("apk_url must be an HTTPS URL without credentials")
badging = subprocess.check_output([args.aapt, "dump", "badging", str(args.apk)], encoding="utf-8")
package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
sdk = re.search(r"sdkVersion:'(\d+)'", badging)
if not package or not sdk:
    parser.error("Cannot read APK package/version/minSdk")
digest = hashlib.sha256()
with args.apk.open("rb") as stream:
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        digest.update(chunk)
args.output.write_text(json.dumps({
    "packageName": package[1], "versionCode": int(package[2]), "versionName": package[3],
    "minSdk": int(sdk[1]), "variant": args.variant, "apkUrl": args.apk_url,
    "sha256": digest.hexdigest(),
}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(args.output)

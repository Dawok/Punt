#!/usr/bin/env python3
"""Package an update feed from the APK's actual Gradle metadata."""
import argparse
import hashlib
import json
import os
from pathlib import Path
from urllib.parse import urlsplit


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--apk-url", required=True)
    args = parser.parse_args()
    if urlsplit(args.apk_url).scheme != "https":
        parser.error("The APK download URL must use HTTPS")
    metadata = json.loads(args.metadata.read_text())
    element = metadata["elements"][0]
    manifest = {
        "versionCode": element["versionCode"],
        "versionName": element["versionName"],
        "apkUrl": args.apk_url,
        "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
    }
    args.output.write_text(json.dumps(manifest, indent=2) + "\n")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as output:
            output.write(f'version_name={manifest["versionName"]}\n')


if __name__ == "__main__":
    main()

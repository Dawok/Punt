#!/usr/bin/env python3
"""Package an update feed from the APK's actual Gradle metadata."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
from urllib.parse import quote, urlsplit


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--release-base-url", required=True)
    args = parser.parse_args()
    base_url = urlsplit(args.release_base_url)
    if base_url.scheme != "https" or not base_url.netloc or base_url.query or base_url.fragment:
        parser.error("The release download base URL must use HTTPS without a query or fragment")
    metadata = json.loads(args.metadata.read_text())
    element = metadata["elements"][0]
    version_name = element["versionName"]
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?", version_name):
        parser.error("versionName must be a version such as 1.2.3 or 1.2.3-beta.1")
    release_tag = f"v{version_name}"
    manifest = {
        "versionCode": element["versionCode"],
        "versionName": version_name,
        "apkUrl": f"{args.release_base_url.rstrip('/')}/{quote(release_tag, safe='')}/punt.apk",
        "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
    }
    args.output.write_text(json.dumps(manifest, indent=2) + "\n")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as output:
            output.write(f'version_name={manifest["versionName"]}\n')
            output.write(f'release_tag={release_tag}\n')


if __name__ == "__main__":
    main()

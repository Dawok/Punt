#!/usr/bin/env bash
set -euo pipefail

keystore="${1:?Missing signing keystore}"
apk="${2:?Missing APK}"
apksigner="${3:?Missing apksigner path}"

expected_signer="$(keytool -exportcert -keystore "$keystore" \
  -storepass android -alias androiddebugkey | sha256sum | awk '{print $1}')"
signature_report="$("$apksigner" verify --print-certs "$apk")"
actual_signers="$(printf '%s\n' "$signature_report" | \
  awk '/^Signer #[0-9]+ certificate SHA-256 digest:/ {print $NF}')"

if [ "$actual_signers" != "$expected_signer" ]; then
  echo '::error::APK signing certificate does not match the saved signing key. Refusing to publish.' >&2
  exit 1
fi

echo "APK signing certificate verified: $expected_signer"

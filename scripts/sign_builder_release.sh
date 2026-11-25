#!/usr/bin/env bash
# Run only on an authorized private signer, never on public CI for actual releases.
set -euo pipefail
umask 077
[[ $# -eq 4 ]] || { echo 'Usage: sign_builder_release.sh INPUT.apk OUTPUT.apk PRIVATE.p12 EXPECTED_SHA256' >&2; exit 2; }
[[ -n "${BUILDER_SIGNING_PASSWORD:-}" ]] || { echo 'A private signing password must be supplied by the authorized secret store.' >&2; exit 2; }
[[ -f "$1" && ! -e "$2" && -f "$3" && "$4" =~ ^[0-9a-fA-F]{64}$ ]]
tool="${APKSIGNER:-apksigner}"
signed=$(mktemp)
trap 'rm -f "$signed"' EXIT
"$tool" sign --ks "$3" --ks-key-alias androidbuilder --ks-pass env:BUILDER_SIGNING_PASSWORD \
   --key-pass env:BUILDER_SIGNING_PASSWORD --out "$signed" "$1"
observed=$("$tool" verify --print-certs "$signed" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
[[ "${observed,,}" == "${4,,}" ]] || { echo 'Certificate mismatch. No signed file was released.' >&2; exit 1; }
cp -n "$signed" "$2"

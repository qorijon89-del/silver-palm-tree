#!/bin/sh
# Provide BUILDER_IMAGE pinned by digest and built with a warmed SDK/cache. Never pass secrets into it.
set -eu
test "$#" = 4
case "$3" in apk|aab) ;; *) exit 2;; esac
case "${BUILDER_IMAGE:-}" in *@sha256:*) ;; *) exit 2;; esac
name="android-builder-$(basename "$(dirname "$1")")"
mkdir -m 700 "$2"
trap 'docker rm -f "$name" >/dev/null 2>&1 || true' EXIT INT TERM
docker run --name "$name" --rm --pull=never --network=none --read-only --cap-drop=ALL \
  --security-opt=no-new-privileges --pids-limit=128 --memory=3g --cpus=2 \
  --user "$(id -u):$(id -g)" --tmpfs /tmp:rw,nosuid,nodev,size=2g \
  --mount "type=bind,src=$1,dst=/input.zip,readonly" --mount "type=bind,src=$2,dst=/out" \
  "$BUILDER_IMAGE" /input.zip /out --format "$3" --permissions "$4" --trusted-project

#!/usr/bin/env bash
#
# Copies the offline speech models to a phone running Open Caption.
#
# Usage:
#   scripts/install-models.sh <folder-with-models> [adb-serial]
#
# The folder must contain these files (see docs/MODELS.md for where to get them):
#   whisper-encoder.int8.onnx
#   whisper-decoder.int8.onnx
#   whisper-tokens.txt
#   silero_vad.onnx
#   denoiser.onnx            (optional: noise reduction)
#
# Open Caption must be installed and opened at least once first, so that its
# app folder exists on the phone.

set -euo pipefail

PACKAGE="tech.briankeya.opencaption"
REMOTE_DIR="/sdcard/Android/data/${PACKAGE}/files/models"
REQUIRED=(whisper-encoder.int8.onnx whisper-decoder.int8.onnx whisper-tokens.txt silero_vad.onnx)
OPTIONAL=(denoiser.onnx)

if [[ $# -lt 1 ]]; then
    sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
fi

SRC="$1"
SERIAL_ARGS=()
if [[ $# -ge 2 ]]; then
    SERIAL_ARGS=(-s "$2")
fi

if ! command -v adb >/dev/null 2>&1; then
    echo "error: adb not found. Install Android platform-tools and add them to PATH." >&2
    exit 1
fi

if [[ ! -d "$SRC" ]]; then
    echo "error: '$SRC' is not a folder." >&2
    exit 1
fi

missing=()
for f in "${REQUIRED[@]}"; do
    [[ -f "$SRC/$f" ]] || missing+=("$f")
done
if [[ ${#missing[@]} -gt 0 ]]; then
    echo "error: missing required model files in '$SRC':" >&2
    printf '  %s\n' "${missing[@]}" >&2
    exit 1
fi

if ! adb "${SERIAL_ARGS[@]}" shell pm path "$PACKAGE" >/dev/null 2>&1; then
    echo "error: $PACKAGE is not installed on the device. Install the app first." >&2
    exit 1
fi

echo "Creating $REMOTE_DIR"
adb "${SERIAL_ARGS[@]}" shell mkdir -p "$REMOTE_DIR"

for f in "${REQUIRED[@]}" "${OPTIONAL[@]}"; do
    if [[ -f "$SRC/$f" ]]; then
        echo "Copying $f"
        adb "${SERIAL_ARGS[@]}" push "$SRC/$f" "$REMOTE_DIR/$f" >/dev/null
    elif [[ " ${REQUIRED[*]} " == *" $f "* ]]; then
        : # already checked above
    else
        echo "Skipping optional $f (not in folder)"
    fi
done

echo "Done. Open Caption will use the offline model the next time captions start."

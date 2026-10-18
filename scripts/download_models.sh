#!/bin/bash
set -euo pipefail

# Downloads the pretrained models used by Segue Core tests and the app into models/.
# Discogs-EffNet is licensed under CC BY-NC-SA 4.0, so it is not committed.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
DEST="$ROOT_DIR/models/discogs-effnet-bsdynamic-1.onnx"
URL="https://essentia.upf.edu/models/music-style-classification/discogs-effnet/discogs-effnet-bsdynamic-1.onnx"

if [ -f "$DEST" ]; then
    echo "Model already exists: $DEST"
    exit 0
fi

mkdir -p "$(dirname "$DEST")"
echo "Downloading $URL ..."
curl -fL --retry 5 --retry-delay 3 -o "$DEST" "$URL"
echo "Saved to $DEST"

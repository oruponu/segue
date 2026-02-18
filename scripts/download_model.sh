#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DEST="$PROJECT_DIR/assets/models/discogs-effnet-bsdynamic-1.onnx"
URL="https://essentia.upf.edu/models/music-style-classification/discogs-effnet/discogs-effnet-bsdynamic-1.onnx"

if [ -f "$DEST" ]; then
    echo "Model already exists: $DEST"
    exit 0
fi

mkdir -p "$(dirname "$DEST")"
echo "Downloading $URL ..."
curl -fL --retry 5 --retry-delay 3 -o "$DEST" "$URL"
echo "Saved to $DEST"

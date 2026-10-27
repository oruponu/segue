#!/bin/bash
set -euo pipefail

# Generates tagged sine-wave test tracks with ffmpeg, pushes them to the device under
# /sdcard/Music/SegueTest/ and waits until MediaStore lists them.
# Run on Linux (on Windows, use WSL): bash scripts/push_test_media.sh [--remove]
#   --remove  deletes /sdcard/Music/SegueTest/ from the device and waits until MediaStore forgets it.
# Honors ANDROID_SERIAL (adb reads it itself) and ADB (path to an adb binary).

EXPECTED_ROWS=6
TIMEOUT_SEC=30
REMOTE_DIR="/sdcard/Music/SegueTest"
# Keep this pattern unexpanded: it is embedded in a command that the device shell parses again
WHERE="relative_path LIKE 'Music/SegueTest/%'"

MODE="push"
case "${1:-}" in
    "") ;;
    --remove) MODE="remove" ;;
    *) echo "Usage: $0 [--remove]" >&2; exit 2 ;;
esac

# WSL usually has no adb of its own, so fall back to the Windows one
if [ -n "${ADB:-}" ]; then
    :
elif command -v adb >/dev/null 2>&1; then
    ADB="adb"
elif command -v adb.exe >/dev/null 2>&1; then
    ADB="adb.exe"
else
    # Default Android Studio SDK location under the Windows %LOCALAPPDATA%
    CMD_EXE="$(command -v cmd.exe || echo /mnt/c/Windows/System32/cmd.exe)"
    win_local="$(cd /mnt/c 2>/dev/null && "${CMD_EXE}" /c 'echo %LOCALAPPDATA%' 2>/dev/null | tr -d '\r' || true)"
    if [ -n "$win_local" ] && [ "$win_local" != '%LOCALAPPDATA%' ]; then
        candidate="$(wslpath "$win_local")/Android/Sdk/platform-tools/adb.exe"
        if [ -x "$candidate" ]; then
            ADB="$candidate"
        fi
    fi
    if [ -z "${ADB:-}" ]; then
        echo "adb not found. Put it on PATH or set ADB=/path/to/adb" >&2
        exit 1
    fi
fi

if [ -z "${ANDROID_SERIAL:-}" ]; then
    # Offline entries (stale emulators) make adb refuse to pick a device, so pick the one online device
    mapfile -t ONLINE < <("$ADB" devices | tr -d '\r' | awk 'NR > 1 && $2 == "device" { print $1 }')
    if [ "${#ONLINE[@]}" -eq 0 ]; then
        echo "No online device. Connect one or check 'adb devices'" >&2
        exit 1
    elif [ "${#ONLINE[@]}" -gt 1 ]; then
        echo "Several devices are online; set ANDROID_SERIAL" >&2
        exit 1
    fi
    export ANDROID_SERIAL="${ONLINE[0]}"
fi
# adb.exe only sees WSL environment variables listed in WSLENV
case "$ADB" in
    *.exe) export WSLENV="ANDROID_SERIAL${WSLENV:+:$WSLENV}" ;;
esac

# adb.exe cannot read WSL paths such as /tmp, so convert host paths when running a Windows adb
host_path() {
    case "$ADB" in
        *.exe) wslpath -w "$1" ;;
        *) printf '%s\n' "$1" ;;
    esac
}

# `adb shell` re-parses its arguments on the device, so each remote command is passed as one string
count_rows() {
    local out
    out="$("$ADB" shell "content query --uri content://media/external/audio/media --projection _id --where \"$WHERE\"" | tr -d '\r')"
    printf '%s\n' "$out" | grep -c '^Row:' || true
}

# The old MEDIA_SCANNER_SCAN_FILE broadcast is ignored on modern Android, so ask MediaProvider directly
scan() {
    "$ADB" shell "content call --uri content://media --method scan_volume --arg external_primary" >/dev/null
}

wait_for_rows() {
    local expected="$1" n=-1 i
    for ((i = 0; i < TIMEOUT_SEC; i++)); do
        n="$(count_rows)"
        if [ "$n" -eq "$expected" ]; then
            echo "MediaStore reports $n rows under Music/SegueTest/"
            return 0
        fi
        sleep 1
    done
    echo "Timed out after ${TIMEOUT_SEC}s: expected $expected rows under Music/SegueTest/, found $n" >&2
    exit 1
}

"$ADB" get-state >/dev/null

if [ "$MODE" = "remove" ]; then
    echo "Removing $REMOTE_DIR from the device ..."
    "$ADB" shell "rm -rf '$REMOTE_DIR'"
    scan
    wait_for_rows 0
    exit 0
fi

command -v ffmpeg >/dev/null 2>&1 || { echo "ffmpeg not found" >&2; exit 1; }

# host_path converts these paths per file when adb is adb.exe
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

FF=(ffmpeg -hide_banner -loglevel error -y)
mkdir -p "$WORK/AlbumA" "$WORK/100%_Hits" "$WORK/Loose"

"${FF[@]}" -f lavfi -i "color=c=red:s=64x64" -frames:v 1 "$WORK/cover.png"

echo "Generating tracks ..."
# AlbumA: mp3 with ID3v2.3 tags and an embedded cover. track/disc keep their "n/total" form
for n in 1 2 3; do
    disc="1/2"
    [ "$n" -eq 3 ] && disc="2/2"
    "${FF[@]}" -f lavfi -i "sine=frequency=$((440 * n)):duration=5" -i "$WORK/cover.png" \
        -map 0:a -map 1:v -c:a libmp3lame -c:v png -disposition:v attached_pic \
        -id3v2_version 3 \
        -metadata title="Segue Test Track $n" \
        -metadata artist="Segue Test Artist $n" \
        -metadata album="Segue Test Album A" \
        -metadata album_artist="Segue Test Artist" \
        -metadata track="$n/3" \
        -metadata disc="$disc" \
        "$WORK/AlbumA/0$n-track.mp3"
done

# 100%_Hits: flac without cover or album artist; the folder name exercises LIKE escaping in tests
for n in 3 4; do
    "${FF[@]}" -f lavfi -i "sine=frequency=$((300 + 100 * n)):duration=5" \
        -c:a flac \
        -metadata title="Segue Test Hit $n" \
        -metadata artist="Segue Test Hits Artist" \
        -metadata album="Segue Test Hits" \
        -metadata track="$n/12" \
        "$WORK/100%_Hits/0$n-hit.flac"
done

# Loose: m4a with no title or album; -map_metadata -1 and bitexact keep ffmpeg from adding tags
"${FF[@]}" -f lavfi -i "sine=frequency=880:duration=5" \
    -map_metadata -1 -fflags +bitexact -flags:a +bitexact -c:a aac \
    "$WORK/Loose/loose.m4a"

echo "Pushing to $REMOTE_DIR ..."
# Start from an empty folder so files left by older runs cannot add extra rows
"$ADB" shell "rm -rf '$REMOTE_DIR'"
"$ADB" shell "mkdir -p '$REMOTE_DIR/AlbumA' '$REMOTE_DIR/100%_Hits' '$REMOTE_DIR/Loose'"
for f in "$WORK"/AlbumA/* "$WORK"/100%_Hits/* "$WORK"/Loose/*; do
    rel="${f#"$WORK"/}"
    "$ADB" push "$(host_path "$f")" "$REMOTE_DIR/$rel" >/dev/null
done

scan
wait_for_rows "$EXPECTED_ROWS"
echo "Done."

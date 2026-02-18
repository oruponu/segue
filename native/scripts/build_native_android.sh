#!/bin/bash
set -euo pipefail

# Usage: ./build_essentia_android.sh [NDK_PATH]

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
NATIVE_DIR="$(dirname "$SCRIPT_DIR")"
BUILD_ROOT="$NATIVE_DIR/build/android"
OUTPUT_DIR="$NATIVE_DIR/libs/android"
INCLUDE_DIR="$NATIVE_DIR/include"

ANDROID_API=28
ABIS=("arm64-v8a")

if [ -n "${1:-}" ]; then
    NDK="$1"
elif [ -n "${ANDROID_NDK_HOME:-}" ]; then
    NDK="$ANDROID_NDK_HOME"
elif [ -n "${ANDROID_HOME:-}" ]; then
    NDK=$(ls -d "$ANDROID_HOME/ndk/"* 2>/dev/null | sort -V | tail -1)
else
    echo "Error: NDK not found. Set ANDROID_NDK_HOME or pass NDK path as argument."
    exit 1
fi
TOOLCHAIN="$NDK/build/cmake/android.toolchain.cmake"

echo "Using NDK: $NDK"
echo "Building for ABIs: ${ABIS[*]}"

# Essentia's waf requires Python <= 3.10
PYTHON_WAF=""
for candidate in python3.10 python3.9 python3.8 python3; do
    if command -v "$candidate" &>/dev/null; then
        major=$("$candidate" -c "import sys; print(sys.version_info[0])")
        minor=$("$candidate" -c "import sys; print(sys.version_info[1])")
        if [ "$major" -eq 3 ] && [ "$minor" -le 10 ]; then
            PYTHON_WAF="$candidate"
            break
        fi
    fi
done
if [ -z "$PYTHON_WAF" ]; then
    echo "Error: Essentia's waf requires Python <= 3.10."
    echo "Install: sudo add-apt-repository ppa:deadsnakes/ppa && sudo apt install python3.10"
    exit 1
fi
echo "Using Python for waf: $PYTHON_WAF ($($PYTHON_WAF --version))"

download_and_extract() {
    local url="$1"
    local dest="$2"
    local strip="${3:-1}"
    local filename
    filename=$(basename "$url")
    local archive="/tmp/$filename"

    if [ ! -d "$dest" ]; then
        mkdir -p "$dest"

        rm -f "$archive"

        echo "Downloading $url ..."
        curl -fL --retry 5 --retry-delay 3 -o "$archive" "$url"

        local filetype
        filetype=$(file -b "$archive")
        if echo "$filetype" | grep -qi "html\|text"; then
            echo "Error: Downloaded file is not an archive ($filetype)"
            echo "URL may be invalid: $url"
            rm -f "$archive"
            rmdir "$dest" 2>/dev/null || true
            exit 1
        fi

        local tar_flag=""
        case "$filename" in
            *.tar.gz|*.tgz)  tar_flag="z" ;;
            *.tar.xz)        tar_flag="J" ;;
            *.tar.bz2)       tar_flag="j" ;;
        esac

        tar "x${tar_flag}f" "$archive" -C "$dest" --strip-components="$strip"
        rm -f "$archive"
    fi
}

abi_to_triple() {
    case "$1" in
        arm64-v8a)   echo "aarch64-linux-android" ;;
        armeabi-v7a) echo "armv7a-linux-androideabi" ;;
        x86_64)      echo "x86_64-linux-android" ;;
    esac
}

abi_to_arch() {
    case "$1" in
        arm64-v8a)   echo "aarch64" ;;
        armeabi-v7a) echo "arm" ;;
        x86_64)      echo "x86_64" ;;
    esac
}

build_fftw3() {
    local ABI=$1
    local TRIPLE=$(abi_to_triple "$ABI")
    local SRC_DIR="$BUILD_ROOT/fftw3-src"
    local BUILD_DIR="$BUILD_ROOT/$ABI/fftw3"
    local PREFIX="$BUILD_ROOT/$ABI/prefix"

    echo "=== Building FFTW3 for $ABI ==="

    download_and_extract "https://www.fftw.org/fftw-3.3.10.tar.gz" "$SRC_DIR"

    mkdir -p "$BUILD_DIR"
    cd "$BUILD_DIR"

    local CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/${TRIPLE}${ANDROID_API}-clang"
    local CXX="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/${TRIPLE}${ANDROID_API}-clang++"

    "$SRC_DIR/configure" \
        --host="$TRIPLE" \
        --prefix="$PREFIX" \
        --enable-float \
        --enable-static \
        --disable-shared \
        --disable-fortran \
        --disable-doc \
        CC="$CC" \
        CXX="$CXX" \
        CFLAGS="-fPIC"

    make -j$(nproc)
    make install
}

build_yaml_cpp() {
    local ABI=$1
    local SRC_DIR="$BUILD_ROOT/yaml-cpp-src"
    local BUILD_DIR="$BUILD_ROOT/$ABI/yaml-cpp"
    local PREFIX="$BUILD_ROOT/$ABI/prefix"

    echo "=== Building yaml-cpp for $ABI ==="

    download_and_extract "https://github.com/jbeder/yaml-cpp/archive/refs/tags/yaml-cpp-0.9.0.tar.gz" "$SRC_DIR"

    mkdir -p "$BUILD_DIR"
    cd "$BUILD_DIR"

    cmake "$SRC_DIR" \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$ANDROID_API" \
        -DCMAKE_INSTALL_PREFIX="$PREFIX" \
        -DYAML_CPP_BUILD_TESTS=OFF \
        -DYAML_CPP_BUILD_TOOLS=OFF \
        -DYAML_BUILD_SHARED_LIBS=OFF \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON

    make -j$(nproc)
    make install
}

build_ffmpeg() {
    local ABI=$1
    local TRIPLE=$(abi_to_triple "$ABI")
    local ARCH=$(abi_to_arch "$ABI")
    local SRC_DIR="$BUILD_ROOT/ffmpeg-src"
    local BUILD_DIR="$BUILD_ROOT/$ABI/ffmpeg"
    local PREFIX="$BUILD_ROOT/$ABI/prefix"

    echo "=== Building FFmpeg for $ABI ==="

    download_and_extract "https://ffmpeg.org/releases/ffmpeg-6.1.4.tar.xz" "$SRC_DIR"

    mkdir -p "$BUILD_DIR"
    cd "$BUILD_DIR"

    local CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/${TRIPLE}${ANDROID_API}-clang"

    "$SRC_DIR/configure" \
        --prefix="$PREFIX" \
        --target-os=android \
        --arch="$ARCH" \
        --cc="$CC" \
        --enable-cross-compile \
        --enable-static \
        --disable-shared \
        --disable-programs \
        --disable-doc \
        --disable-everything \
        --enable-demuxer=mp3,flac,ogg,aac,wav,mov,matroska \
        --enable-decoder=mp3,mp3float,flac,aac,vorbis,opus,pcm_s16le,pcm_s24le,pcm_s32le,pcm_f32le \
        --enable-parser=mp3,flac,aac,vorbis,opus \
        --enable-protocol=file \
        --disable-avdevice \
        --disable-swscale \
        --disable-postproc \
        --disable-avfilter \
        --disable-network \
        --enable-pic \
        --disable-asm

    make -j$(nproc)
    make install
}

build_essentia() {
    local ABI=$1
    local TRIPLE=$(abi_to_triple "$ABI")
    local SRC_DIR="$BUILD_ROOT/essentia-src"
    local PREFIX="$BUILD_ROOT/$ABI/prefix"

    echo "=== Building Essentia for $ABI ==="

    download_and_extract "https://github.com/MTG/essentia/archive/refs/tags/v2.1_beta5.tar.gz" "$SRC_DIR"

    cd "$SRC_DIR"

    local CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/${TRIPLE}${ANDROID_API}-clang"
    local CXX="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/${TRIPLE}${ANDROID_API}-clang++"

    $PYTHON_WAF waf configure \
        --cross-compile-android \
        --lightweight=fftw,yaml \
        --prefix="$PREFIX" \
        --pkg-config-path="$PREFIX/lib/pkgconfig" \
        --fft=FFTW \
        --build-static \
        CC="$CC" \
        CXX="$CXX" \
        CXXFLAGS="-fPIC -I$PREFIX/include" \
        LDFLAGS="-L$PREFIX/lib"

    $PYTHON_WAF waf build -j$(nproc)
    $PYTHON_WAF waf install

    if [ ! -d "$INCLUDE_DIR/essentia" ]; then
        cp -r "$PREFIX/include/essentia" "$INCLUDE_DIR/"
    fi
}

ORT_VERSION="1.21.1"

download_onnxruntime() {
    local ABI=$1
    local ORT_DIR="$BUILD_ROOT/onnxruntime"
    local AAR_URL="https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/${ORT_VERSION}/onnxruntime-android-${ORT_VERSION}.aar"
    local AAR_FILE="/tmp/onnxruntime-android-${ORT_VERSION}.aar"
    local DEST_LIB="$OUTPUT_DIR/$ABI"
    local JNILIBS_DIR="$NATIVE_DIR/../android/app/src/main/jniLibs/$ABI"

    echo "=== Downloading ONNX Runtime ${ORT_VERSION} for $ABI ==="

    if [ -f "$DEST_LIB/libonnxruntime.so" ] && [ -d "$INCLUDE_DIR/onnxruntime" ]; then
        echo "ONNX Runtime already present, skipping"
        return
    fi

    if [ ! -f "$AAR_FILE" ]; then
        echo "Downloading $AAR_URL ..."
        curl -fL --retry 5 --retry-delay 3 -o "$AAR_FILE" "$AAR_URL"
    fi

    rm -rf "$ORT_DIR"
    mkdir -p "$ORT_DIR"
    unzip -q "$AAR_FILE" -d "$ORT_DIR"

    mkdir -p "$DEST_LIB"
    cp "$ORT_DIR/jni/$ABI/libonnxruntime.so" "$DEST_LIB/"

    rm -rf "$INCLUDE_DIR/onnxruntime"
    mkdir -p "$INCLUDE_DIR/onnxruntime"
    cp "$ORT_DIR/headers/"*.h "$INCLUDE_DIR/onnxruntime/"

    # Copy to jniLibs for APK packaging
    mkdir -p "$JNILIBS_DIR"
    cp "$DEST_LIB/libonnxruntime.so" "$JNILIBS_DIR/"

    rm -f "$AAR_FILE"
    rm -rf "$ORT_DIR"
}

copy_libs() {
    local ABI=$1
    local PREFIX="$BUILD_ROOT/$ABI/prefix"
    local DEST="$OUTPUT_DIR/$ABI"

    echo "=== Copying libraries for $ABI ==="
    mkdir -p "$DEST"

    cp "$PREFIX/lib/libessentia.a" "$DEST/"
    cp "$PREFIX/lib/libfftw3f.a" "$DEST/"
    cp "$PREFIX/lib/libyaml-cpp.a" "$DEST/"
    cp "$PREFIX/lib/libavcodec.a" "$DEST/"
    cp "$PREFIX/lib/libavformat.a" "$DEST/"
    cp "$PREFIX/lib/libavutil.a" "$DEST/"
    cp "$PREFIX/lib/libswresample.a" "$DEST/"

    if [ ! -d "$INCLUDE_DIR/libavcodec" ]; then
        cp -r "$PREFIX/include/libavcodec" "$INCLUDE_DIR/"
        cp -r "$PREFIX/include/libavformat" "$INCLUDE_DIR/"
        cp -r "$PREFIX/include/libavutil" "$INCLUDE_DIR/"
        cp -r "$PREFIX/include/libswresample" "$INCLUDE_DIR/"
    fi

    echo "Libraries copied to $DEST"
}

for ABI in "${ABIS[@]}"; do
    build_fftw3 "$ABI"
    build_yaml_cpp "$ABI"
    build_ffmpeg "$ABI"
    build_essentia "$ABI"
    download_onnxruntime "$ABI"
    copy_libs "$ABI"
done

echo ""
echo "=== Android build complete ==="
echo "Static libraries: $OUTPUT_DIR"
echo "Headers: $INCLUDE_DIR/essentia"
echo "Headers: $INCLUDE_DIR/onnxruntime"

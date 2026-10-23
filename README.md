# Segue

楽曲解析機能付きモバイル向け音楽プレーヤー

[![License](https://img.shields.io/badge/license-AGPL--3.0-blue.svg)](LICENSE)
![Status](https://img.shields.io/badge/status-WIP-yellow)

## 概要

Segue はモバイル向けの音楽プレーヤーです。楽曲解析には [Essentia](https://essentia.upf.edu/) を使用しています。

## システム要件

- 依存ライブラリのビルド用に、Linux 環境（Windows では WSL）と次のもの
  - `build-essential cmake pkg-config unzip curl file python3`
  - Linux 版の [Android NDK](https://developer.android.com/ndk/downloads)

## ビルドと実行

ネイティブの依存ライブラリ（FFmpeg、Essentia など）はコミットしていないため、最初に Linux でビルドします。成果物は `core/deps/` に出力されます。手順の詳細は [core/README.md](core/README.md) にあります。

```bash
git clone https://github.com/oruponu/segue.git
cd segue
bash scripts/download_models.sh
# 依存ライブラリをビルドする（ANDROID_NDK_HOME に Linux 版の NDK を設定しておく）
SEGUE_DEPS_BUILD_ROOT=$HOME/segue-deps-build bash core/scripts/build_deps.sh android
```

## ライセンス

[AGPL-3.0 License](LICENSE)

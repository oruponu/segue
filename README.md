# Segue

楽曲解析機能付きモバイル向け音楽プレーヤー

[![License](https://img.shields.io/badge/license-AGPL--3.0-blue.svg)](LICENSE)
![Status](https://img.shields.io/badge/status-WIP-yellow)

## 概要

Segue はモバイル向けの音楽プレーヤーです。楽曲解析には [Essentia](https://essentia.upf.edu/) を使用しています。

## 構成

| ディレクトリ | 内容 |
|---|---|
| `core/` | C++ 製の解析エンジン（Segue Core）。詳細は [core/README.md](core/README.md) を参照 |
| `android/` | Android アプリ（Jetpack Compose） |
| `scripts/` | モデルのダウンロードや実機テスト用音源の転送に使用するスクリプト |

## システム要件

- Linux 環境（Windows では WSL）。依存ライブラリのビルドに使用します
  - `build-essential cmake pkg-config unzip curl file python3`
  - Linux 版の [Android NDK](https://developer.android.com/ndk/downloads)
- [Android Studio](https://developer.android.com/studio)（同梱の JBR と Android SDK を使用）
- Android 13 以降の端末（`arm64-v8a`）

## ビルドと実行

FFmpeg や Essentia などのネイティブ依存ライブラリはリポジトリに含めていないため、最初に Linux でビルドする必要があります。ビルドしたライブラリは `core/deps/` に配置されます。詳しくは [core/README.md](core/README.md) を参照してください。

```bash
git clone https://github.com/oruponu/segue.git
cd segue
bash scripts/download_models.sh
# 依存ライブラリをビルドする（ANDROID_NDK_HOME に Linux 版の NDK を設定しておく）
SEGUE_DEPS_BUILD_ROOT=$HOME/segue-deps-build bash core/scripts/build_deps.sh android
```

ビルドが終わったら、端末を接続してアプリをインストールします。Gradle の実行前に、`JAVA_HOME` に Android Studio 同梱の JBR を設定してください。

```bash
cd android
./gradlew :app:installDebug
```

## テスト

JVM 上のユニットテストは、次のコマンドで実行できます。

```bash
cd android
./gradlew :app:testDebugUnitTest
```

実機テストでは、事前にテスト用の音源を端末の `Music/SegueTest/` に転送します。転送スクリプトは Linux（Windows では WSL）で実行し、`ffmpeg` を使用します。テスト後は `--remove` を指定することで、転送した音源を削除できます。

リポジトリのルートから実行してください。

```bash
bash scripts/push_test_media.sh
cd android
./gradlew :app:connectedDebugAndroidTest :core-bridge:connectedDebugAndroidTest
```

## ライセンス

[AGPL-3.0 License](LICENSE)

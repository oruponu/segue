# Segue

楽曲解析機能付きモバイル向け音楽プレーヤー

[![License](https://img.shields.io/badge/license-AGPL--3.0-blue.svg)](LICENSE)
![Status](https://img.shields.io/badge/status-WIP-yellow)

## 概要

Segue は、[Flutter](https://flutter.dev/) ベースの音楽プレーヤーです。楽曲解析には [Essentia](https://essentia.upf.edu/) を使用しています。

## システム要件

- [Flutter SDK](https://docs.flutter.dev/get-started/install)

## ビルドと実行

```bash
git clone https://github.com/oruponu/segue.git
cd segue
flutter pub get
bash scripts/download_model.sh
dart run build_runner build
flutter run
```

## ライセンス

[AGPL-3.0 License](LICENSE)

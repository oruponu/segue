# Segue Core

The Segue analysis engine (C++17). It decodes audio, computes the waveform, spectrum and peak data for display, and analyzes BPM, key and style (Discogs-EffNet). Apps use it only through the C API in `include/segue/segue_core.h`.

| Directory | Contents |
|---|---|
| `include/segue/` | Public C API |
| `src/` | Implementation |
| `spec/` | Analysis specs (cache format, key notation, style label table). The apps follow them |
| `testdata/` | Test audio and the legacy results (`expected.json`) |
| `legacy/` | Native API of the legacy Flutter app. It will be removed together with the Flutter app |
| `scripts/build_deps.sh` | Builds the dependencies |

## Building the dependencies

On Linux (on Windows, use WSL), install `build-essential cmake pkg-config unzip curl file python3` (Ubuntu package names) and the Linux Android NDK, set `ANDROID_NDK_HOME`, then run:

```bash
SEGUE_DEPS_BUILD_ROOT=$HOME/segue-deps-build bash core/scripts/build_deps.sh android
```

The output goes to `core/deps/` (not committed). `SEGUE_DEPS_BUILD_ROOT` keeps the sources and intermediate files outside the repository.

## Tests

The tests run as Android instrumented tests on an arm64 device. Connect the device, then run:

```bash
bash scripts/download_models.sh
cd android
./gradlew :core-bridge:connectedDebugAndroidTest
```

`style-labels.json` in `spec/` is generated from the legacy Flutter app's label list:

```bash
python3 core/spec/generate_style_labels.py
```

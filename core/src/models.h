#pragma once

#include <onnxruntime_c_api.h>

#include <string>

#include "segue/segue_core.h"

// ONNX Runtime session for Discogs-EffNet. Loaded once and reused for every analysis
struct SegueModels {
  const OrtApi* ort = nullptr;
  OrtEnv* env = nullptr;
  OrtSessionOptions* options = nullptr;
  OrtSession* session = nullptr;
  OrtMemoryInfo* memoryInfo = nullptr;
  std::string inputName;
  std::string outputName;

  SegueModels() = default;
  SegueModels(const SegueModels&) = delete;
  SegueModels& operator=(const SegueModels&) = delete;
  ~SegueModels();
};

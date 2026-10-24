#include "models.h"

#include <cstdint>
#include <exception>
#include <memory>
#include <vector>

#include "log.h"

namespace {

constexpr int64_t kPatchFrames = 128;
constexpr int64_t kMelBands = 96;
constexpr int64_t kStyleClasses = 400;

bool succeeded(const OrtApi* ort, OrtStatus* status, const char* what) {
  if (status == nullptr) return true;
  SEGUE_LOGE("%s failed: %s", what, ort->GetErrorMessage(status));
  ort->ReleaseStatus(status);
  return false;
}

// Frees a name allocated by the ORT allocator, even when an exception is thrown
struct OrtNameDeleter {
  const OrtApi* ort;
  OrtAllocator* allocator;
  void operator()(char* name) const {
    if (name == nullptr) return;
    OrtStatus* status = ort->AllocatorFree(allocator, name);
    if (status != nullptr) ort->ReleaseStatus(status);
  }
};
using OrtName = std::unique_ptr<char, OrtNameDeleter>;

// Takes ownership of typeInfo and always releases it
bool readTensorInfo(const OrtApi* ort, OrtTypeInfo* typeInfo, ONNXTensorElementDataType& type,
                    std::vector<int64_t>& dims) {
  const OrtTensorTypeAndShapeInfo* info = nullptr;
  size_t count = 0;
  bool ok =
      succeeded(ort, ort->CastTypeInfoToTensorInfo(typeInfo, &info), "CastTypeInfoToTensorInfo") &&
      info != nullptr &&
      succeeded(ort, ort->GetTensorElementType(info, &type), "GetTensorElementType") &&
      succeeded(ort, ort->GetDimensionsCount(info, &count), "GetDimensionsCount");
  if (ok) {
    dims.assign(count, 0);
    ok = succeeded(ort, ort->GetDimensions(info, dims.data(), count), "GetDimensions");
  }
  ort->ReleaseTypeInfo(typeInfo);
  return ok;
}

// A negative dimension is dynamic (any size matches)
bool dimMatches(int64_t actual, int64_t expected) { return actual < 0 || actual == expected; }

// Checks the Discogs-EffNet contract: a single float input of [batch, 128, 96], and a float output
// 0 whose last dimension is 400
bool validateContract(const OrtApi* ort, OrtSession* session) {
  size_t inputs = 0;
  size_t outputs = 0;
  if (!succeeded(ort, ort->SessionGetInputCount(session, &inputs), "SessionGetInputCount") ||
      !succeeded(ort, ort->SessionGetOutputCount(session, &outputs), "SessionGetOutputCount") ||
      inputs != 1 || outputs < 1) {
    SEGUE_LOGE("Unexpected model: %zu inputs, %zu outputs", inputs, outputs);
    return false;
  }
  ONNXTensorElementDataType type = ONNX_TENSOR_ELEMENT_DATA_TYPE_UNDEFINED;
  std::vector<int64_t> dims;
  OrtTypeInfo* typeInfo = nullptr;
  if (!succeeded(ort, ort->SessionGetInputTypeInfo(session, 0, &typeInfo),
                 "SessionGetInputTypeInfo") ||
      !readTensorInfo(ort, typeInfo, type, dims) || type != ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT ||
      dims.size() != 3 || !dimMatches(dims[1], kPatchFrames) || !dimMatches(dims[2], kMelBands)) {
    SEGUE_LOGE("Unexpected model input");
    return false;
  }
  typeInfo = nullptr;
  if (!succeeded(ort, ort->SessionGetOutputTypeInfo(session, 0, &typeInfo),
                 "SessionGetOutputTypeInfo") ||
      !readTensorInfo(ort, typeInfo, type, dims) || type != ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT ||
      dims.empty() || !dimMatches(dims.back(), kStyleClasses)) {
    SEGUE_LOGE("Unexpected model output");
    return false;
  }
  return true;
}

SegueStatus loadModels(const char* effnetPath, SegueModels** out) {
  auto models = std::make_unique<SegueModels>();
  models->ort = OrtGetApiBase()->GetApi(ORT_API_VERSION);
  const OrtApi* ort = models->ort;
  if (ort == nullptr) return SEGUE_MODEL_LOAD_FAILED;

  if (!succeeded(ort, ort->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "segue", &models->env),
                 "CreateEnv") ||
      !succeeded(ort, ort->CreateSessionOptions(&models->options), "CreateSessionOptions") ||
      !succeeded(ort,
                 ort->CreateSession(models->env, effnetPath, models->options, &models->session),
                 "CreateSession") ||
      !succeeded(
          ort, ort->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &models->memoryInfo),
          "CreateCpuMemoryInfo")) {
    return SEGUE_MODEL_LOAD_FAILED;
  }
  if (!validateContract(ort, models->session)) return SEGUE_MODEL_LOAD_FAILED;

  OrtAllocator* allocator = nullptr;
  if (!succeeded(ort, ort->GetAllocatorWithDefaultOptions(&allocator), "GetAllocator")) {
    return SEGUE_MODEL_LOAD_FAILED;
  }
  char* rawInput = nullptr;
  if (!succeeded(ort, ort->SessionGetInputName(models->session, 0, allocator, &rawInput),
                 "InputName")) {
    return SEGUE_MODEL_LOAD_FAILED;
  }
  OrtName inputName(rawInput, OrtNameDeleter{ort, allocator});
  char* rawOutput = nullptr;
  if (!succeeded(ort, ort->SessionGetOutputName(models->session, 0, allocator, &rawOutput),
                 "OutputName")) {
    return SEGUE_MODEL_LOAD_FAILED;
  }
  OrtName outputName(rawOutput, OrtNameDeleter{ort, allocator});
  if (!inputName || !outputName) return SEGUE_MODEL_LOAD_FAILED;
  models->inputName = inputName.get();
  models->outputName = outputName.get();

  *out = models.release();
  return SEGUE_OK;
}

}  // namespace

SegueModels::~SegueModels() {
  if (ort == nullptr) return;
  if (memoryInfo != nullptr) ort->ReleaseMemoryInfo(memoryInfo);
  if (session != nullptr) ort->ReleaseSession(session);
  if (options != nullptr) ort->ReleaseSessionOptions(options);
  if (env != nullptr) ort->ReleaseEnv(env);
}

extern "C" {

SegueStatus segue_models_load(const char* effnet_path, SegueModels** out) {
  if (out == nullptr) return SEGUE_INVALID_ARGUMENT;
  *out = nullptr;
  if (effnet_path == nullptr) return SEGUE_INVALID_ARGUMENT;
  try {
    return loadModels(effnet_path, out);
  } catch (const std::exception& e) {
    SEGUE_LOGE("Model load failed: %s", e.what());
    return SEGUE_MODEL_LOAD_FAILED;
  } catch (...) {
    return SEGUE_MODEL_LOAD_FAILED;
  }
}

void segue_models_free(SegueModels* models) { delete models; }

}  // extern "C"

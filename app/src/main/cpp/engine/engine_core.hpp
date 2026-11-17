#pragma once

#include <cstdint>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

namespace storm {

struct InputFrame {
    float moveX = 0.0f;
    float moveY = 0.0f;
    bool attack = false;
    bool jump = false;
    bool attackPressed = false;
    bool jumpPressed = false;
    uint32_t activeTouches = 0;
};

class InputSystem {
public:
    void pushTouch(int32_t id, float x, float y, int32_t action);
    InputFrame snapshot();
    void clear();

private:
    struct Touch { float x = 0, y = 0, anchorX = 0, anchorY = 0; };
    std::mutex mutex_;
    std::unordered_map<int32_t, Touch> touches_;
    bool previousAttack_ = false;
    bool previousJump_ = false;
};

struct Vec3 { float x = 0, y = 0, z = 0; };
struct Entity { uint32_t id = 0; Vec3 position{}; bool active = true; };

class EngineRuntime {
public:
    void start();
    void stop();
    void advance(int64_t monotonicNs, const InputFrame& input);
    std::string report() const;

private:
    mutable std::mutex mutex_;
    std::vector<Entity> entities_;
    bool running_ = false;
    int64_t lastNs_ = 0;
    double accumulator_ = 0;
    uint64_t renderFrames_ = 0;
    uint64_t fixedUpdates_ = 0;
};

class AssetStore {
public:
    void setRoot(std::string root);
    bool loadRelative(const std::string& relativePath);
    std::string report() const;

private:
    mutable std::mutex mutex_;
    std::string root_;
    size_t cachedBytes_ = 0;
    size_t memoryLimitBytes_ = 128u * 1024u * 1024u;
    std::unordered_map<std::string, std::vector<uint8_t>> cache_;
};

}  // namespace storm

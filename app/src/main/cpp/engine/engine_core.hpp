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
    bool block = false;
    bool special = false;
    bool attackPressed = false;
    bool jumpPressed = false;
    bool specialPressed = false;
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
    bool previousSpecial_ = false;
};

struct Vec3 { float x = 0, y = 0, z = 0; };

struct GameState {
    Vec3 player{};
    Vec3 opponent{2.6f, 0.0f, 0.0f};
    float playerHealth = 1.0f;
    float opponentHealth = 1.0f;
    float chakra = 0.35f;
    float playerYaw = 1.5707963f;
    float opponentYaw = -1.5707963f;
    float playerAttackTime = 0.0f;
    float opponentAttackTime = 0.0f;
    float specialTime = 0.0f;
    float elapsedSeconds = 0.0f;
    bool blocking = false;
    bool gameOver = false;
    bool playerWon = false;
    uint64_t playerHits = 0;
    uint64_t opponentHits = 0;
};

class EngineRuntime {
public:
    void start();
    void stop();
    void reset();
    void advance(int64_t monotonicNs, const InputFrame& input);
    Vec3 actorPosition() const;
    GameState gameState() const;
    std::string report() const;

private:
    void step(float dt, const InputFrame& input);
    mutable std::mutex mutex_;
    GameState state_{};
    bool running_ = false;
    int64_t lastNs_ = 0;
    double accumulator_ = 0;
    uint64_t renderFrames_ = 0;
    uint64_t fixedUpdates_ = 0;
    float playerAttackCooldown_ = 0.0f;
    float opponentAttackCooldown_ = 0.7f;
    float specialCooldown_ = 0.0f;
    float playerVerticalVelocity_ = 0.0f;
    float opponentStun_ = 0.0f;
};

class AssetStore {
public:
    void setRoot(std::string root);
    bool loadRelative(const std::string& relativePath);
    bool readRelative(const std::string& relativePath,
                      std::vector<uint8_t>& output);
    std::string report() const;

private:
    mutable std::mutex mutex_;
    std::string root_;
    size_t cachedBytes_ = 0;
    size_t memoryLimitBytes_ = 128u * 1024u * 1024u;
    std::unordered_map<std::string, std::vector<uint8_t>> cache_;
};

}  // namespace storm

#include "engine_core.hpp"

#include <algorithm>
#include <cmath>
#include <fstream>
#include <sstream>
#include <utility>

namespace storm {
namespace {
float clampAxis(float value) {
    if (std::abs(value) < 0.08f) return 0.0f;
    return std::max(-1.0f, std::min(1.0f, value));
}
constexpr double kFixedStep = 1.0 / 60.0;
constexpr float kPi = 3.14159265358979323846f;

bool isSafeRelativePath(const std::string& path) {
    return !path.empty() && path.front() != '/' &&
           path.find("..") == std::string::npos &&
           path.find('\\') == std::string::npos &&
           path.find(':') == std::string::npos;
}
float distanceXZ(const Vec3& a, const Vec3& b) {
    const float dx = a.x - b.x;
    const float dz = a.z - b.z;
    return std::sqrt(dx * dx + dz * dz);
}
float clampArena(float value, float limit) {
    return std::max(-limit, std::min(limit, value));
}
}

void InputSystem::pushTouch(int32_t id, float x, float y, int32_t action) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (action == 3) { touches_.clear(); return; }
    if (action == 0 || action == 5) {
        touches_[id] = Touch{x, y, x, y};
        return;
    }
    if (action == 2) {
        auto it = touches_.find(id);
        if (it != touches_.end()) { it->second.x = x; it->second.y = y; }
        return;
    }
    if (action == 1 || action == 6) touches_.erase(id);
}

InputFrame InputSystem::snapshot() {
    std::lock_guard<std::mutex> lock(mutex_);
    InputFrame frame{};
    frame.activeTouches = static_cast<uint32_t>(touches_.size());
    for (const auto& item : touches_) {
        const Touch& touch = item.second;
        if (touch.anchorX < 0.48f) {
            frame.moveX = clampAxis((touch.x - touch.anchorX) * 3.2f);
            frame.moveY = clampAxis((touch.y - touch.anchorY) * 3.2f);
        } else if (touch.anchorY < 0.25f) {
            frame.jump = true;
        } else if (touch.anchorY < 0.50f) {
            frame.attack = true;
        } else if (touch.anchorY < 0.75f) {
            frame.block = true;
        } else {
            frame.special = true;
        }
    }
    frame.attackPressed = frame.attack && !previousAttack_;
    frame.jumpPressed = frame.jump && !previousJump_;
    frame.specialPressed = frame.special && !previousSpecial_;
    previousAttack_ = frame.attack;
    previousJump_ = frame.jump;
    previousSpecial_ = frame.special;
    return frame;
}

void InputSystem::clear() {
    std::lock_guard<std::mutex> lock(mutex_);
    touches_.clear();
    previousAttack_ = previousJump_ = previousSpecial_ = false;
}

void EngineRuntime::start() {
    std::lock_guard<std::mutex> lock(mutex_);
    running_ = true;
    lastNs_ = 0;
    accumulator_ = 0;
    renderFrames_ = fixedUpdates_ = 0;
    state_ = GameState{};
    state_.player = {-2.6f, 0.0f, 0.0f};
    state_.opponent = {2.6f, 0.0f, 0.0f};
    state_.playerYaw = kPi * 0.5f;
    state_.opponentYaw = -kPi * 0.5f;
    playerAttackCooldown_ = 0.0f;
    opponentAttackCooldown_ = 0.7f;
    specialCooldown_ = 0.0f;
    playerVerticalVelocity_ = 0.0f;
    opponentStun_ = 0.0f;
}

void EngineRuntime::stop() {
    std::lock_guard<std::mutex> lock(mutex_);
    running_ = false;
    lastNs_ = 0;
    accumulator_ = 0;
}

void EngineRuntime::reset() {
    std::lock_guard<std::mutex> lock(mutex_);
    state_ = GameState{};
    state_.player = {-2.6f, 0.0f, 0.0f};
    state_.opponent = {2.6f, 0.0f, 0.0f};
    state_.playerYaw = kPi * 0.5f;
    state_.opponentYaw = -kPi * 0.5f;
    playerAttackCooldown_ = 0.0f;
    opponentAttackCooldown_ = 0.7f;
    specialCooldown_ = 0.0f;
    playerVerticalVelocity_ = 0.0f;
    opponentStun_ = 0.0f;
}

void EngineRuntime::step(float dt, const InputFrame& input) {
    if (state_.gameOver) return;

    playerAttackCooldown_ = std::max(0.0f, playerAttackCooldown_ - dt);
    opponentAttackCooldown_ = std::max(0.0f, opponentAttackCooldown_ - dt);
    specialCooldown_ = std::max(0.0f, specialCooldown_ - dt);
    opponentStun_ = std::max(0.0f, opponentStun_ - dt);
    state_.playerAttackTime = std::max(0.0f, state_.playerAttackTime - dt);
    state_.opponentAttackTime = std::max(0.0f, state_.opponentAttackTime - dt);
    state_.specialTime = std::max(0.0f, state_.specialTime - dt);
    state_.blocking = input.block;
    state_.elapsedSeconds += dt;
    state_.chakra = std::min(1.0f, state_.chakra + 0.018f * dt);

    const float movementLength = std::min(
        1.0f, std::sqrt(input.moveX * input.moveX + input.moveY * input.moveY));
    if (movementLength > 0.01f) {
        state_.player.x += input.moveX * 4.0f * dt;
        state_.player.z += input.moveY * 4.0f * dt;
    }
    state_.player.x = clampArena(state_.player.x, 7.0f);
    state_.player.z = clampArena(state_.player.z, 5.0f);

    if (input.jumpPressed && state_.player.y <= 0.001f) {
        playerVerticalVelocity_ = 6.2f;
    }
    playerVerticalVelocity_ -= 16.0f * dt;
    state_.player.y = std::max(0.0f, state_.player.y + playerVerticalVelocity_ * dt);
    if (state_.player.y <= 0.0f) playerVerticalVelocity_ = 0.0f;

    const float dx = state_.opponent.x - state_.player.x;
    const float dz = state_.opponent.z - state_.player.z;
    const float distance = std::max(0.001f, std::sqrt(dx * dx + dz * dz));
    state_.playerYaw = std::atan2(dx, dz);
    state_.opponentYaw = std::atan2(-dx, -dz);

    if (input.attackPressed && playerAttackCooldown_ <= 0.0f) {
        playerAttackCooldown_ = 0.34f;
        state_.playerAttackTime = 0.27f;
        if (distance < 2.45f && state_.opponentHealth > 0.0f) {
            state_.opponentHealth = std::max(0.0f, state_.opponentHealth - 0.105f);
            state_.opponent.x += (dx / distance) * 0.34f;
            state_.opponent.z += (dz / distance) * 0.34f;
            opponentStun_ = 0.18f;
            state_.chakra = std::min(1.0f, state_.chakra + 0.055f);
            ++state_.playerHits;
        }
    }

    if (input.specialPressed && specialCooldown_ <= 0.0f &&
        state_.chakra >= 0.25f) {
        specialCooldown_ = 2.4f;
        state_.chakra -= 0.25f;
        state_.specialTime = 0.62f;
        if (distance < 4.1f && state_.opponentHealth > 0.0f) {
            state_.opponentHealth = std::max(0.0f, state_.opponentHealth - 0.245f);
            state_.opponent.x += (dx / distance) * 0.85f;
            state_.opponent.z += (dz / distance) * 0.85f;
            opponentStun_ = 0.42f;
            ++state_.playerHits;
        }
    }

    state_.opponent.x = clampArena(state_.opponent.x, 7.0f);
    state_.opponent.z = clampArena(state_.opponent.z, 5.0f);

    if (state_.opponentHealth > 0.0f && opponentStun_ <= 0.0f) {
        const float ex = state_.player.x - state_.opponent.x;
        const float ez = state_.player.z - state_.opponent.z;
        const float enemyDistance = std::max(0.001f, std::sqrt(ex * ex + ez * ez));
        if (enemyDistance > 1.72f) {
            const float speed = enemyDistance > 6.0f ? 1.9f : 1.35f;
            state_.opponent.x += (ex / enemyDistance) * speed * dt;
            state_.opponent.z += (ez / enemyDistance) * speed * dt;
        } else if (opponentAttackCooldown_ <= 0.0f) {
            opponentAttackCooldown_ = 1.05f;
            state_.opponentAttackTime = 0.32f;
            const float damage = input.block ? 0.018f : 0.085f;
            state_.playerHealth = std::max(0.0f, state_.playerHealth - damage);
            if (!input.block) {
                state_.player.x -= (ex / enemyDistance) * 0.24f;
                state_.player.z -= (ez / enemyDistance) * 0.24f;
            }
            ++state_.opponentHits;
        }
    }

    state_.opponent.x = clampArena(state_.opponent.x, 7.0f);
    state_.opponent.z = clampArena(state_.opponent.z, 5.0f);
    if (state_.playerHealth <= 0.0f || state_.opponentHealth <= 0.0f) {
        state_.gameOver = true;
        state_.playerWon = state_.opponentHealth <= 0.0f;
    }
}

void EngineRuntime::advance(int64_t nowNs, const InputFrame& input) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!running_) return;
    if (lastNs_ == 0) { lastNs_ = nowNs; ++renderFrames_; return; }

    const int64_t elapsedNs = std::max<int64_t>(
        0, std::min<int64_t>(nowNs - lastNs_, 250000000));
    lastNs_ = nowNs;
    accumulator_ += static_cast<double>(elapsedNs) / 1.0e9;
    ++renderFrames_;

    uint32_t steps = 0;
    while (accumulator_ >= kFixedStep && steps < 5) {
        step(static_cast<float>(kFixedStep), input);
        accumulator_ -= kFixedStep;
        ++fixedUpdates_;
        ++steps;
    }
    if (steps == 5 && accumulator_ > kFixedStep)
        accumulator_ = std::fmod(accumulator_, kFixedStep);
}

Vec3 EngineRuntime::actorPosition() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return state_.player;
}

GameState EngineRuntime::gameState() const {
    std::lock_guard<std::mutex> lock(mutex_);
    return state_;
}

std::string EngineRuntime::report() const {
    std::lock_guard<std::mutex> lock(mutex_);
    std::ostringstream out;
    out << "Storm Combat Prototype 0.11\n"
        << "Simulation: fixed 60 Hz\n"
        << "Render frames: " << renderFrames_
        << "\nFixed updates: " << fixedUpdates_
        << "\nPlayer HP: " << static_cast<int>(state_.playerHealth * 100.0f)
        << "% | Rival HP: " << static_cast<int>(state_.opponentHealth * 100.0f)
        << "% | Chakra: " << static_cast<int>(state_.chakra * 100.0f) << "%"
        << "\nPlayer hits: " << state_.playerHits
        << " | Rival hits: " << state_.opponentHits
        << "\nResult: " << (state_.gameOver
            ? (state_.playerWon ? "PLAYER WIN" : "PLAYER LOSE")
            : "FIGHTING");
    return out.str();
}

void AssetStore::setRoot(std::string root) {
    std::lock_guard<std::mutex> lock(mutex_);
    root_ = std::move(root);
}

bool AssetStore::readRelative(const std::string& path,
                              std::vector<uint8_t>& output) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (root_.empty() || !isSafeRelativePath(path)) return false;

    const auto cached = cache_.find(path);
    if (cached != cache_.end()) {
        output = cached->second;
        return true;
    }

    std::ifstream input(root_ + "/" + path, std::ios::binary | std::ios::ate);
    if (!input) return false;
    const std::streamoff size = input.tellg();
    if (size < 0) return false;
    const size_t bytes = static_cast<size_t>(size);
    if (bytes > memoryLimitBytes_ ||
        cachedBytes_ > memoryLimitBytes_ - bytes) return false;

    std::vector<uint8_t> data(bytes);
    input.seekg(0, std::ios::beg);
    if (bytes > 0 && !input.read(
            reinterpret_cast<char*>(data.data()),
            static_cast<std::streamsize>(bytes))) return false;

    cachedBytes_ += bytes;
    auto inserted = cache_.emplace(path, std::move(data));
    output = inserted.first->second;
    return true;
}

bool AssetStore::loadRelative(const std::string& relativePath) {
    std::vector<uint8_t> ignored;
    return readRelative(relativePath, ignored);
}

std::string AssetStore::report() const {
    std::lock_guard<std::mutex> lock(mutex_);
    std::ostringstream out;
    out << "Asset cache: " << cache_.size() << " files / " << cachedBytes_
        << " bytes\nRoot: " << (root_.empty() ? "not set" : "app-private storage")
        << "\nCache budget: " << memoryLimitBytes_ << " bytes";
    return out.str();
}

}  // namespace storm

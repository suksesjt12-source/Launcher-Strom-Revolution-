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
}

void InputSystem::pushTouch(int32_t id, float x, float y, int32_t action) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (action == 3) { touches_.clear(); return; } // ACTION_CANCEL
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
        } else if (touch.y < 0.52f) {
            frame.jump = true;
        } else {
            frame.attack = true;
        }
    }
    frame.attackPressed = frame.attack && !previousAttack_;
    frame.jumpPressed = frame.jump && !previousJump_;
    previousAttack_ = frame.attack;
    previousJump_ = frame.jump;
    return frame;
}

void InputSystem::clear() {
    std::lock_guard<std::mutex> lock(mutex_);
    touches_.clear();
    previousAttack_ = false;
    previousJump_ = false;
}

void EngineRuntime::start() {
    std::lock_guard<std::mutex> lock(mutex_);
    running_ = true;
    lastNs_ = 0;
    accumulator_ = 0;
    renderFrames_ = fixedUpdates_ = 0;
    if (entities_.empty()) entities_.push_back(Entity{1, {}, true});
}

void EngineRuntime::stop() {
    std::lock_guard<std::mutex> lock(mutex_);
    running_ = false;
    lastNs_ = 0;
    accumulator_ = 0;
}

void EngineRuntime::advance(int64_t nowNs, const InputFrame& input) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!running_) return;
    if (lastNs_ == 0) { lastNs_ = nowNs; ++renderFrames_; return; }

    int64_t elapsedNs = std::max<int64_t>(0, std::min<int64_t>(nowNs - lastNs_, 250000000));
    lastNs_ = nowNs;
    accumulator_ += static_cast<double>(elapsedNs) / 1.0e9;
    ++renderFrames_;

    uint32_t steps = 0;
    while (accumulator_ >= kFixedStep && steps < 5) {
        if (!entities_.empty() && entities_[0].active) {
            entities_[0].position.x += input.moveX * 3.0f * static_cast<float>(kFixedStep);
            entities_[0].position.z += input.moveY * 3.0f * static_cast<float>(kFixedStep);
            entities_[0].position.x = std::max(-10.0f, std::min(10.0f, entities_[0].position.x));
            entities_[0].position.z = std::max(-10.0f, std::min(10.0f, entities_[0].position.z));
        }
        accumulator_ -= kFixedStep;
        ++fixedUpdates_;
        ++steps;
    }
    if (steps == 5 && accumulator_ > kFixedStep) accumulator_ = std::fmod(accumulator_, kFixedStep);
}

std::string EngineRuntime::report() const {
    std::lock_guard<std::mutex> lock(mutex_);
    std::ostringstream out;
    out << "Storm Engine Core\nState: " << (running_ ? "RUNNING" : "STOPPED")
        << "\nSimulation: fixed 60 Hz\nRender frames: " << renderFrames_
        << "\nFixed updates: " << fixedUpdates_
        << "\nScene entities: " << entities_.size();
    if (!entities_.empty()) {
        out << "\nSandbox actor: " << entities_[0].position.x
            << ", 0, " << entities_[0].position.z;
    }
    return out.str();
}

void AssetStore::setRoot(std::string root) {
    std::lock_guard<std::mutex> lock(mutex_);
    root_ = std::move(root);
}

bool AssetStore::loadRelative(const std::string& path) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (root_.empty() || path.empty() || path.front() == '/' ||
        path.find("..") != std::string::npos ||
        path.find('\\') != std::string::npos ||
        path.find(':') != std::string::npos) return false;
    if (cache_.find(path) != cache_.end()) return true;

    std::ifstream input(root_ + "/" + path, std::ios::binary | std::ios::ate);
    if (!input) return false;
    const std::streamoff size = input.tellg();
    if (size < 0) return false;
    const size_t bytes = static_cast<size_t>(size);
    if (bytes > memoryLimitBytes_ || cachedBytes_ > memoryLimitBytes_ - bytes) return false;

    std::vector<uint8_t> data(bytes);
    input.seekg(0, std::ios::beg);
    if (bytes > 0 && !input.read(reinterpret_cast<char*>(data.data()),
                                 static_cast<std::streamsize>(bytes))) return false;
    cachedBytes_ += bytes;
    cache_.emplace(path, std::move(data));
    return true;
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

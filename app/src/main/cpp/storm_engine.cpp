#define VK_USE_PLATFORM_ANDROID_KHR

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <jni.h>
#include <vulkan/vulkan.h>

#include "engine/engine_core.hpp"

#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#define LOG_TAG "StormVulkan"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex gRendererMutex;
std::atomic<bool> gStopRenderer{false};
std::thread gRendererThread;
storm::EngineRuntime gEngineRuntime;
storm::InputSystem gInputSystem;
storm::AssetStore gAssetStore;

struct Vertex {
    float position[3];
    float normal[3];
};

struct Mat4 {
    float value[16]{};
};

struct DrawPushConstants {
    Mat4 mvp;
    float tint[4];
};

static_assert(sizeof(Mat4) == 64, "Mat4 must be tightly packed");
static_assert(sizeof(DrawPushConstants) <= 128,
              "Vulkan guarantees at least 128 push-constant bytes");

bool hasDeviceExtension(VkPhysicalDevice device, const char* requestedName) {
    uint32_t count = 0;
    VkResult result = vkEnumerateDeviceExtensionProperties(
        device, nullptr, &count, nullptr);
    if (result != VK_SUCCESS || count == 0) return false;

    std::vector<VkExtensionProperties> extensions(count);
    result = vkEnumerateDeviceExtensionProperties(
        device, nullptr, &count, extensions.data());
    if (result != VK_SUCCESS) return false;

    for (const auto& extension : extensions) {
        if (std::string(extension.extensionName) == requestedName) return true;
    }
    return false;
}

std::string queryVulkanDevices() {
    uint32_t loaderVersion = VK_API_VERSION_1_0;
    auto enumerateVersion = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
        vkGetInstanceProcAddr(VK_NULL_HANDLE, "vkEnumerateInstanceVersion"));
    if (enumerateVersion != nullptr &&
        enumerateVersion(&loaderVersion) != VK_SUCCESS) {
        loaderVersion = VK_API_VERSION_1_0;
    }

    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "Storm Revolution Launcher";
    appInfo.applicationVersion = VK_MAKE_VERSION(0, 6, 0);
    appInfo.pEngineName = "Storm Native Vulkan";
    appInfo.engineVersion = VK_MAKE_VERSION(0, 6, 0);
    appInfo.apiVersion = VK_API_VERSION_1_1;

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;

    VkInstance instance = VK_NULL_HANDLE;
    const VkResult createResult =
        vkCreateInstance(&createInfo, nullptr, &instance);

    std::string report = "Vulkan loader: " +
        std::to_string(VK_API_VERSION_MAJOR(loaderVersion)) + "." +
        std::to_string(VK_API_VERSION_MINOR(loaderVersion)) + "\n";

    if (createResult != VK_SUCCESS || instance == VK_NULL_HANDLE) {
        report += "Vulkan instance: gagal (" +
            std::to_string(static_cast<int>(createResult)) + ")\n";
        report += "GPU gate: tidak dapat diperiksa";
        return report;
    }

    uint32_t deviceCount = 0;
    VkResult result =
        vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr);
    if (result != VK_SUCCESS || deviceCount == 0) {
        report += "Vulkan physical device: tidak ditemukan\n";
        report += "GPU gate: tidak dapat diperiksa";
        vkDestroyInstance(instance, nullptr);
        return report;
    }

    std::vector<VkPhysicalDevice> devices(deviceCount);
    result = vkEnumeratePhysicalDevices(instance, &deviceCount, devices.data());
    if (result != VK_SUCCESS) {
        report += "Gagal membaca daftar GPU\n";
        report += "GPU gate: tidak dapat diperiksa";
        vkDestroyInstance(instance, nullptr);
        return report;
    }

    report += "GPU Vulkan terdeteksi: " + std::to_string(deviceCount) + "\n";
    for (VkPhysicalDevice device : devices) {
        VkPhysicalDeviceProperties properties{};
        vkGetPhysicalDeviceProperties(device, &properties);
        report += "- GPU: ";
        report += properties.deviceName;
        report += "\n  Vulkan API: " +
            std::to_string(VK_API_VERSION_MAJOR(properties.apiVersion)) + "." +
            std::to_string(VK_API_VERSION_MINOR(properties.apiVersion));
        report += "\n  Swapchain: ";
        report += hasDeviceExtension(device, VK_KHR_SWAPCHAIN_EXTENSION_NAME)
            ? "didukung" : "tidak didukung";
        report += "\n";
    }
    report += "Vulkan instance: berhasil\n";
    report += "Pemeriksaan GPU bukan bukti game dapat berjalan.";
    vkDestroyInstance(instance, nullptr);
    return report;
}

float dot3(const std::array<float, 3>& a,
           const std::array<float, 3>& b) {
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
}

std::array<float, 3> subtract3(const std::array<float, 3>& a,
                               const std::array<float, 3>& b) {
    return {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
}

std::array<float, 3> cross3(const std::array<float, 3>& a,
                            const std::array<float, 3>& b) {
    return {a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]};
}

std::array<float, 3> normalize3(const std::array<float, 3>& value) {
    const float length = std::sqrt(std::max(0.000001f, dot3(value, value)));
    return {value[0] / length, value[1] / length, value[2] / length};
}

Mat4 multiply(const Mat4& a, const Mat4& b) {
    Mat4 result{};
    for (int column = 0; column < 4; ++column) {
        for (int row = 0; row < 4; ++row) {
            for (int k = 0; k < 4; ++k) {
                result.value[column * 4 + row] +=
                    a.value[k * 4 + row] * b.value[column * 4 + k];
            }
        }
    }
    return result;
}

Mat4 translation(float x, float y, float z) {
    Mat4 result{};
    result.value[0] = result.value[5] = result.value[10] = 1.0f;
    result.value[15] = 1.0f;
    result.value[12] = x;
    result.value[13] = y;
    result.value[14] = z;
    return result;
}

Mat4 scaleMatrix(float x, float y, float z) {
    Mat4 result{};
    result.value[0] = x;
    result.value[5] = y;
    result.value[10] = z;
    result.value[15] = 1.0f;
    return result;
}

Mat4 rotationY(float radians) {
    Mat4 result{};
    const float c = std::cos(radians);
    const float s = std::sin(radians);
    result.value[0] = c;
    result.value[2] = -s;
    result.value[5] = 1.0f;
    result.value[8] = s;
    result.value[10] = c;
    result.value[15] = 1.0f;
    return result;
}

Mat4 perspective(float fovRadians, float aspect,
                 float nearPlane, float farPlane) {
    Mat4 result{};
    const float f = 1.0f / std::tan(fovRadians * 0.5f);
    result.value[0] = f / std::max(0.1f, aspect);
    result.value[5] = -f;
    result.value[10] = farPlane / (nearPlane - farPlane);
    result.value[11] = -1.0f;
    result.value[14] = (farPlane * nearPlane) /
                       (nearPlane - farPlane);
    return result;
}

Mat4 lookAt(const std::array<float, 3>& eye,
            const std::array<float, 3>& center) {
    const std::array<float, 3> up{0.0f, 1.0f, 0.0f};
    const auto forward = normalize3(subtract3(center, eye));
    const auto side = normalize3(cross3(forward, up));
    const auto upVector = cross3(side, forward);

    Mat4 result{};
    result.value[0] = side[0];
    result.value[1] = upVector[0];
    result.value[2] = -forward[0];
    result.value[4] = side[1];
    result.value[5] = upVector[1];
    result.value[6] = -forward[1];
    result.value[8] = side[2];
    result.value[9] = upVector[2];
    result.value[10] = -forward[2];
    result.value[12] = -dot3(side, eye);
    result.value[13] = -dot3(upVector, eye);
    result.value[14] = dot3(forward, eye);
    result.value[15] = 1.0f;
    return result;
}

std::vector<Vertex> createSphereMesh() {
    constexpr int slices = 18;
    constexpr int stacks = 12;
    constexpr float pi = 3.14159265358979323846f;

    std::vector<std::vector<Vertex>> grid(
        stacks + 1, std::vector<Vertex>(slices + 1));

    for (int y = 0; y <= stacks; ++y) {
        const float v = static_cast<float>(y) / stacks;
        const float phi = v * pi;
        for (int x = 0; x <= slices; ++x) {
            const float u = static_cast<float>(x) / slices;
            const float theta = u * 2.0f * pi;
            const float nx = std::sin(phi) * std::cos(theta);
            const float ny = std::cos(phi);
            const float nz = std::sin(phi) * std::sin(theta);
            grid[y][x] = Vertex{{nx, ny, nz}, {nx, ny, nz}};
        }
    }

    std::vector<Vertex> vertices;
    vertices.reserve(stacks * slices * 6);
    for (int y = 0; y < stacks; ++y) {
        for (int x = 0; x < slices; ++x) {
            const Vertex& a = grid[y][x];
            const Vertex& b = grid[y + 1][x];
            const Vertex& c = grid[y + 1][x + 1];
            const Vertex& d = grid[y][x + 1];
            vertices.push_back(a);
            vertices.push_back(b);
            vertices.push_back(c);
            vertices.push_back(a);
            vertices.push_back(c);
            vertices.push_back(d);
        }
    }
    return vertices;
}

bool findMemoryType(VkPhysicalDevice physicalDevice, uint32_t typeFilter,
                    VkMemoryPropertyFlags properties, uint32_t& index) {
    VkPhysicalDeviceMemoryProperties memoryProperties{};
    vkGetPhysicalDeviceMemoryProperties(physicalDevice, &memoryProperties);
    for (uint32_t i = 0; i < memoryProperties.memoryTypeCount; ++i) {
        const bool allowed = (typeFilter & (1u << i)) != 0;
        const bool matches =
            (memoryProperties.memoryTypes[i].propertyFlags & properties) ==
            properties;
        if (allowed && matches) {
            index = i;
            return true;
        }
    }
    return false;
}

bool createVertexBuffer(VkPhysicalDevice physicalDevice, VkDevice device,
                        const std::vector<Vertex>& vertices,
                        VkBuffer& buffer, VkDeviceMemory& memory) {
    const VkDeviceSize byteCount =
        static_cast<VkDeviceSize>(vertices.size() * sizeof(Vertex));

    VkBufferCreateInfo bufferInfo{};
    bufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufferInfo.size = byteCount;
    bufferInfo.usage = VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
    bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    if (vkCreateBuffer(device, &bufferInfo, nullptr, &buffer) != VK_SUCCESS)
        return false;

    VkMemoryRequirements requirements{};
    vkGetBufferMemoryRequirements(device, buffer, &requirements);
    uint32_t memoryType = 0;
    if (!findMemoryType(physicalDevice, requirements.memoryTypeBits,
                        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT |
                            VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                        memoryType)) {
        return false;
    }

    VkMemoryAllocateInfo allocateInfo{};
    allocateInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocateInfo.allocationSize = requirements.size;
    allocateInfo.memoryTypeIndex = memoryType;
    if (vkAllocateMemory(device, &allocateInfo, nullptr, &memory) != VK_SUCCESS)
        return false;
    if (vkBindBufferMemory(device, buffer, memory, 0) != VK_SUCCESS)
        return false;

    void* mapped = nullptr;
    if (vkMapMemory(device, memory, 0, byteCount, 0, &mapped) != VK_SUCCESS)
        return false;
    std::memcpy(mapped, vertices.data(), static_cast<size_t>(byteCount));
    vkUnmapMemory(device, memory);
    return true;
}

VkFormat chooseDepthFormat(VkPhysicalDevice physicalDevice) {
    const std::array<VkFormat, 3> candidates = {
        VK_FORMAT_D32_SFLOAT,
        VK_FORMAT_D24_UNORM_S8_UINT,
        VK_FORMAT_D16_UNORM
    };
    for (VkFormat format : candidates) {
        VkFormatProperties properties{};
        vkGetPhysicalDeviceFormatProperties(
            physicalDevice, format, &properties);
        if ((properties.optimalTilingFeatures &
             VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0) {
            return format;
        }
    }
    return VK_FORMAT_UNDEFINED;
}

bool createDepthResources(VkPhysicalDevice physicalDevice, VkDevice device,
                          VkExtent2D extent, VkFormat format,
                          VkImage& image, VkDeviceMemory& memory,
                          VkImageView& view) {
    VkImageCreateInfo imageInfo{};
    imageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    imageInfo.imageType = VK_IMAGE_TYPE_2D;
    imageInfo.format = format;
    imageInfo.extent = {extent.width, extent.height, 1};
    imageInfo.mipLevels = 1;
    imageInfo.arrayLayers = 1;
    imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
    imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    if (vkCreateImage(device, &imageInfo, nullptr, &image) != VK_SUCCESS)
        return false;

    VkMemoryRequirements requirements{};
    vkGetImageMemoryRequirements(device, image, &requirements);
    uint32_t memoryType = 0;
    if (!findMemoryType(physicalDevice, requirements.memoryTypeBits,
                        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT, memoryType)) {
        return false;
    }

    VkMemoryAllocateInfo allocateInfo{};
    allocateInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocateInfo.allocationSize = requirements.size;
    allocateInfo.memoryTypeIndex = memoryType;
    if (vkAllocateMemory(device, &allocateInfo, nullptr, &memory) != VK_SUCCESS)
        return false;
    if (vkBindImageMemory(device, image, memory, 0) != VK_SUCCESS)
        return false;

    VkImageViewCreateInfo viewInfo{};
    viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    viewInfo.image = image;
    viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
    viewInfo.format = format;
    viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT;
    viewInfo.subresourceRange.baseMipLevel = 0;
    viewInfo.subresourceRange.levelCount = 1;
    viewInfo.subresourceRange.baseArrayLayer = 0;
    viewInfo.subresourceRange.layerCount = 1;
    return vkCreateImageView(device, &viewInfo, nullptr, &view) == VK_SUCCESS;
}

VkShaderModule createShaderModule(VkDevice device,
                                  const std::vector<uint8_t>& bytes) {
    if (bytes.empty() || bytes.size() % sizeof(uint32_t) != 0)
        return VK_NULL_HANDLE;
    VkShaderModuleCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    createInfo.codeSize = bytes.size();
    createInfo.pCode = reinterpret_cast<const uint32_t*>(bytes.data());
    VkShaderModule module = VK_NULL_HANDLE;
    if (vkCreateShaderModule(device, &createInfo, nullptr, &module) != VK_SUCCESS)
        return VK_NULL_HANDLE;
    return module;
}

Mat4 objectMvp(const Mat4& viewProjection, float x, float y, float z,
               float sx, float sy, float sz, float yaw) {
    const Mat4 model = multiply(
        translation(x, y, z),
        multiply(rotationY(yaw), scaleMatrix(sx, sy, sz)));
    return multiply(viewProjection, model);
}

void drawSphere(VkCommandBuffer commandBuffer, VkPipelineLayout pipelineLayout,
                uint32_t vertexCount, const Mat4& viewProjection,
                float x, float y, float z, float sx, float sy, float sz,
                float yaw, const std::array<float, 3>& tint) {
    DrawPushConstants push{};
    push.mvp = objectMvp(
        viewProjection, x, y, z, sx, sy, sz, yaw);
    push.tint[0] = tint[0];
    push.tint[1] = tint[1];
    push.tint[2] = tint[2];
    push.tint[3] = 1.0f;
    vkCmdPushConstants(commandBuffer, pipelineLayout,
                       VK_SHADER_STAGE_VERTEX_BIT, 0,
                       sizeof(DrawPushConstants), &push);
    vkCmdDraw(commandBuffer, vertexCount, 1, 0, 0);
}

void drawSandboxScene(VkCommandBuffer commandBuffer,
                      VkPipelineLayout pipelineLayout,
                      VkBuffer vertexBuffer, uint32_t vertexCount,
                      const Mat4& viewProjection,
                      const storm::Vec3& actor,
                      const storm::InputFrame& input,
                      float seconds) {
    const VkDeviceSize offset = 0;
    vkCmdBindVertexBuffers(commandBuffer, 0, 1, &vertexBuffer, &offset);

    const float movement = std::min(
        1.0f, std::sqrt(input.moveX * input.moveX +
                        input.moveY * input.moveY));
    const float walk = std::sin(seconds * 9.0f) * movement * 0.20f;
    const float bob = std::sin(seconds * 3.2f) * 0.025f;
    const float yaw = movement > 0.08f
        ? std::atan2(input.moveX, input.moveY)
        : std::sin(seconds * 0.7f) * 0.035f;

    // A compact low-poly test actor and circular platform, not game assets.
    drawSphere(commandBuffer, pipelineLayout, vertexCount, viewProjection,
               actor.x, actor.y - 0.14f, actor.z,
               3.4f, 0.10f, 2.6f, 0.0f, {0.12f, 0.16f, 0.23f});
    drawSphere(commandBuffer, pipelineLayout, vertexCount, viewProjection,
               actor.x, actor.y + 0.025f, actor.z,
               0.43f, 0.035f, 0.31f, 0.0f, {0.055f, 0.07f, 0.11f});

    // Legs and boots.
    drawSphere(commandBuffer, pipelineLayout, vertexCount, viewProjection,
               actor.x - 0.17f, actor.y + 0.43f + bob, actor.z,
               0.13f, 0.36f + walk * 0.18f, 0.14f,
               walk, {0.12f, 0.20f, 0.34f});
    drawSphere(commandBuffer, pipelineLayout, vertexCount, viewProjection,
               actor.x + 0.17f, actor.y + 0.43f + bob, actor.z,
               0.13f, 0.36f - walk * 0.18f, 0.14f,
               -walk, {0.12f, 0.20f, 0.34f});
    drawSphere(commandBuffer, pipelineLayout, vertexCount, viewProjection,
               actor.x - 0.17f, actor.y + 0.12f, actor.z + 0.045f,
               0.16f, 0.11f, 0.22f, 0.0f, {0.055f, 0.075f, 0.12f});
    drawSphere(commandBuffer, pipelineLayout, vertexCount, viewProjection,
               actor.x + 0.17f, actor.y + 0.12f, actor.z + 0.045f,
               0.16f, 0.11f, 0.22f, 0.0f, {0.055f, 0.075f, 0.12f});

    // Torso, shoulder pieces and arms.
    drawSphere(commandBuffer, pipelineLayout, vertexCount, viewProjection,
               actor.x, actor.y + 1.15f + bob, actor.z,
               0.36f, 0.53f, 0.23f, yaw, {0.16f, 0.24f, 0.38f});
    drawSphere(commandBuffer, pipelineLayout, vertexCount, viewProjection,
               actor.x - 0.39f, actor.y + 1.43f + bob, actor.z,
               0.16f, 0.17f, 0.17f, 0.0f, {0.90f, 0.30f, 0.08f});
    drawSphere(commandBuffer, pipelineLayout, vertexCount, viewProjection,
               actor.x + 0.39f, actor.y + 1.43f + bob, actor.z,
               0.16f, 0.17f, 0.17f, 0.0f, {0.90f, 0.30f, 0.08f});
    drawSphere(commandBuffer, pipelineLayout, vertexCount, viewProjection,
               actor.x - 0.48f, actor.y + 1.12f + bob, actor.z,
               0.13f, 0.34f, 0.13f, -walk, {0.82f, 0.27f, 0.075f});
    drawSphere(commandBuffer, pipelineLayout, vertexCount, viewProjection,
               actor.x + 0.48f, actor.y + 1.12f + bob, actor.z,
               0.13f, 0.34f, 0.13f, walk, {0.82f, 0.27f, 0.075f});
    drawSphere(commandBuffer, pipelineLayout, vertexCount, viewProjection,
               actor.x - 0.50f, actor.y + 0.82f + bob, actor.z + 0.02f,
               0.13f, 0.14f, 0.13f, 0.0f, {0.78f, 0.58f, 0.40f});
    drawSphere(commandBuffer, pipelineLayout, vertexCount, viewProjection,
               actor.x + 0.50f, actor.y + 0.82f + bob, actor.z + 0.02f,
               0.13f, 0.14f, 0.13f, 0.0f, {0.78f, 0.58f, 0.40f});

    // Head and a simple headband accent.
    drawSphere(commandBuffer, pipelineLayout, vertexCount, viewProjection,
               actor.x, actor.y + 1.91f + bob, actor.z,
               0.235f, 0.27f, 0.23f, yaw, {0.78f, 0.58f, 0.40f});
    drawSphere(commandBuffer, pipelineLayout, vertexCount, viewProjection,
               actor.x, actor.y + 2.10f + bob, actor.z,
               0.245f, 0.065f, 0.24f, yaw, {0.87f, 0.31f, 0.075f});
}

void renderLoop(ANativeWindow* window) {
    if (window == nullptr) return;

    VkInstance instance = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkRenderPass renderPass = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout = VK_NULL_HANDLE;
    VkPipeline graphicsPipeline = VK_NULL_HANDLE;
    VkShaderModule vertexShader = VK_NULL_HANDLE;
    VkShaderModule fragmentShader = VK_NULL_HANDLE;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    VkSemaphore imageAvailable = VK_NULL_HANDLE;
    VkSemaphore renderFinished = VK_NULL_HANDLE;
    VkFence inFlight = VK_NULL_HANDLE;
    VkBuffer vertexBuffer = VK_NULL_HANDLE;
    VkDeviceMemory vertexMemory = VK_NULL_HANDLE;
    VkImage depthImage = VK_NULL_HANDLE;
    VkDeviceMemory depthMemory = VK_NULL_HANDLE;
    VkImageView depthView = VK_NULL_HANDLE;
    std::vector<VkImageView> imageViews;
    std::vector<VkFramebuffer> framebuffers;
    VkQueue graphicsQueue = VK_NULL_HANDLE;
    VkQueue presentQueue = VK_NULL_HANDLE;

    auto cleanup = [&]() {
        gEngineRuntime.stop();
        if (device != VK_NULL_HANDLE) vkDeviceWaitIdle(device);
        if (device != VK_NULL_HANDLE && graphicsPipeline != VK_NULL_HANDLE)
            vkDestroyPipeline(device, graphicsPipeline, nullptr);
        if (device != VK_NULL_HANDLE && pipelineLayout != VK_NULL_HANDLE)
            vkDestroyPipelineLayout(device, pipelineLayout, nullptr);
        if (device != VK_NULL_HANDLE && vertexShader != VK_NULL_HANDLE)
            vkDestroyShaderModule(device, vertexShader, nullptr);
        if (device != VK_NULL_HANDLE && fragmentShader != VK_NULL_HANDLE)
            vkDestroyShaderModule(device, fragmentShader, nullptr);
        if (device != VK_NULL_HANDLE && vertexBuffer != VK_NULL_HANDLE)
            vkDestroyBuffer(device, vertexBuffer, nullptr);
        if (device != VK_NULL_HANDLE && vertexMemory != VK_NULL_HANDLE)
            vkFreeMemory(device, vertexMemory, nullptr);
        if (device != VK_NULL_HANDLE && depthView != VK_NULL_HANDLE)
            vkDestroyImageView(device, depthView, nullptr);
        if (device != VK_NULL_HANDLE && depthImage != VK_NULL_HANDLE)
            vkDestroyImage(device, depthImage, nullptr);
        if (device != VK_NULL_HANDLE && depthMemory != VK_NULL_HANDLE)
            vkFreeMemory(device, depthMemory, nullptr);
        if (device != VK_NULL_HANDLE && inFlight != VK_NULL_HANDLE)
            vkDestroyFence(device, inFlight, nullptr);
        if (device != VK_NULL_HANDLE && imageAvailable != VK_NULL_HANDLE)
            vkDestroySemaphore(device, imageAvailable, nullptr);
        if (device != VK_NULL_HANDLE && renderFinished != VK_NULL_HANDLE)
            vkDestroySemaphore(device, renderFinished, nullptr);
        if (device != VK_NULL_HANDLE && commandPool != VK_NULL_HANDLE)
            vkDestroyCommandPool(device, commandPool, nullptr);
        if (device != VK_NULL_HANDLE) {
            for (VkFramebuffer framebuffer : framebuffers)
                vkDestroyFramebuffer(device, framebuffer, nullptr);
            if (renderPass != VK_NULL_HANDLE)
                vkDestroyRenderPass(device, renderPass, nullptr);
            for (VkImageView view : imageViews)
                vkDestroyImageView(device, view, nullptr);
            if (swapchain != VK_NULL_HANDLE)
                vkDestroySwapchainKHR(device, swapchain, nullptr);
            vkDestroyDevice(device, nullptr);
        }
        if (instance != VK_NULL_HANDLE && surface != VK_NULL_HANDLE)
            vkDestroySurfaceKHR(instance, surface, nullptr);
        if (instance != VK_NULL_HANDLE) vkDestroyInstance(instance, nullptr);
        ANativeWindow_release(window);
    };

    const char* instanceExtensions[] = {
        VK_KHR_SURFACE_EXTENSION_NAME,
        VK_KHR_ANDROID_SURFACE_EXTENSION_NAME
    };
    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "Storm Native 3D Preview";
    appInfo.applicationVersion = VK_MAKE_VERSION(0, 6, 0);
    appInfo.pEngineName = "Storm Vulkan Renderer";
    appInfo.engineVersion = VK_MAKE_VERSION(0, 6, 0);
    appInfo.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo instanceInfo{};
    instanceInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    instanceInfo.pApplicationInfo = &appInfo;
    instanceInfo.enabledExtensionCount = 2;
    instanceInfo.ppEnabledExtensionNames = instanceExtensions;
    VkResult result = vkCreateInstance(&instanceInfo, nullptr, &instance);
    if (result != VK_SUCCESS) {
        LOGE("vkCreateInstance failed: %d", result);
        cleanup();
        return;
    }

    VkAndroidSurfaceCreateInfoKHR surfaceInfo{};
    surfaceInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    surfaceInfo.window = window;
    result = vkCreateAndroidSurfaceKHR(instance, &surfaceInfo, nullptr, &surface);
    if (result != VK_SUCCESS) {
        LOGE("vkCreateAndroidSurfaceKHR failed: %d", result);
        cleanup();
        return;
    }

    uint32_t physicalCount = 0;
    result = vkEnumeratePhysicalDevices(instance, &physicalCount, nullptr);
    if (result != VK_SUCCESS || physicalCount == 0) {
        LOGE("No Vulkan physical devices");
        cleanup();
        return;
    }
    std::vector<VkPhysicalDevice> physicalDevices(physicalCount);
    result = vkEnumeratePhysicalDevices(
        instance, &physicalCount, physicalDevices.data());
    if (result != VK_SUCCESS) {
        LOGE("vkEnumeratePhysicalDevices failed: %d", result);
        cleanup();
        return;
    }

    uint32_t queueFamilyIndex = UINT32_MAX;
    for (VkPhysicalDevice candidate : physicalDevices) {
        if (!hasDeviceExtension(candidate, VK_KHR_SWAPCHAIN_EXTENSION_NAME))
            continue;
        uint32_t familyCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(
            candidate, &familyCount, nullptr);
        std::vector<VkQueueFamilyProperties> families(familyCount);
        vkGetPhysicalDeviceQueueFamilyProperties(
            candidate, &familyCount, families.data());
        for (uint32_t i = 0; i < familyCount; ++i) {
            VkBool32 presentSupported = VK_FALSE;
            const VkResult supportResult =
                vkGetPhysicalDeviceSurfaceSupportKHR(
                    candidate, i, surface, &presentSupported);
            if (supportResult == VK_SUCCESS &&
                (families[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0 &&
                presentSupported == VK_TRUE) {
                physicalDevice = candidate;
                queueFamilyIndex = i;
                break;
            }
        }
        if (physicalDevice != VK_NULL_HANDLE) break;
    }

    if (physicalDevice == VK_NULL_HANDLE || queueFamilyIndex == UINT32_MAX) {
        LOGE("No graphics/present queue with swapchain support");
        cleanup();
        return;
    }

    const float queuePriority = 1.0f;
    VkDeviceQueueCreateInfo queueInfo{};
    queueInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    queueInfo.queueFamilyIndex = queueFamilyIndex;
    queueInfo.queueCount = 1;
    queueInfo.pQueuePriorities = &queuePriority;

    const char* deviceExtensions[] = { VK_KHR_SWAPCHAIN_EXTENSION_NAME };
    VkDeviceCreateInfo deviceInfo{};
    deviceInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    deviceInfo.queueCreateInfoCount = 1;
    deviceInfo.pQueueCreateInfos = &queueInfo;
    deviceInfo.enabledExtensionCount = 1;
    deviceInfo.ppEnabledExtensionNames = deviceExtensions;
    result = vkCreateDevice(physicalDevice, &deviceInfo, nullptr, &device);
    if (result != VK_SUCCESS) {
        LOGE("vkCreateDevice failed: %d", result);
        cleanup();
        return;
    }
    vkGetDeviceQueue(device, queueFamilyIndex, 0, &graphicsQueue);
    presentQueue = graphicsQueue;

    VkSurfaceCapabilitiesKHR capabilities{};
    result = vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
        physicalDevice, surface, &capabilities);
    if (result != VK_SUCCESS ||
        (capabilities.supportedUsageFlags &
         VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) == 0) {
        LOGE("Surface does not support color attachment usage");
        cleanup();
        return;
    }

    uint32_t formatCount = 0;
    result = vkGetPhysicalDeviceSurfaceFormatsKHR(
        physicalDevice, surface, &formatCount, nullptr);
    if (result != VK_SUCCESS || formatCount == 0) {
        LOGE("No surface formats");
        cleanup();
        return;
    }
    std::vector<VkSurfaceFormatKHR> formats(formatCount);
    vkGetPhysicalDeviceSurfaceFormatsKHR(
        physicalDevice, surface, &formatCount, formats.data());

    VkSurfaceFormatKHR surfaceFormat = formats[0];
    for (const auto& format : formats) {
        if (format.format == VK_FORMAT_R8G8B8A8_UNORM &&
            format.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
            surfaceFormat = format;
            break;
        }
    }

    VkExtent2D extent{};
    if (capabilities.currentExtent.width != UINT32_MAX) {
        extent = capabilities.currentExtent;
    } else {
        const uint32_t width =
            static_cast<uint32_t>(ANativeWindow_getWidth(window));
        const uint32_t height =
            static_cast<uint32_t>(ANativeWindow_getHeight(window));
        extent.width = std::clamp(
            width, capabilities.minImageExtent.width,
            capabilities.maxImageExtent.width);
        extent.height = std::clamp(
            height, capabilities.minImageExtent.height,
            capabilities.maxImageExtent.height);
    }

    uint32_t imageCount = capabilities.minImageCount + 1;
    if (capabilities.maxImageCount > 0 &&
        imageCount > capabilities.maxImageCount) {
        imageCount = capabilities.maxImageCount;
    }

    VkSwapchainCreateInfoKHR swapchainInfo{};
    swapchainInfo.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    swapchainInfo.surface = surface;
    swapchainInfo.minImageCount = imageCount;
    swapchainInfo.imageFormat = surfaceFormat.format;
    swapchainInfo.imageColorSpace = surfaceFormat.colorSpace;
    swapchainInfo.imageExtent = extent;
    swapchainInfo.imageArrayLayers = 1;
    swapchainInfo.imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    swapchainInfo.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    swapchainInfo.preTransform = capabilities.currentTransform;
    swapchainInfo.compositeAlpha =
        (capabilities.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
            ? VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR
            : static_cast<VkCompositeAlphaFlagBitsKHR>(
                capabilities.supportedCompositeAlpha &
                (~capabilities.supportedCompositeAlpha + 1));
    swapchainInfo.presentMode = VK_PRESENT_MODE_FIFO_KHR;
    swapchainInfo.clipped = VK_TRUE;
    result = vkCreateSwapchainKHR(
        device, &swapchainInfo, nullptr, &swapchain);
    if (result != VK_SUCCESS) {
        LOGE("vkCreateSwapchainKHR failed: %d", result);
        cleanup();
        return;
    }

    uint32_t swapImageCount = 0;
    vkGetSwapchainImagesKHR(device, swapchain, &swapImageCount, nullptr);
    std::vector<VkImage> images(swapImageCount);
    vkGetSwapchainImagesKHR(
        device, swapchain, &swapImageCount, images.data());

    imageViews.resize(swapImageCount);
    framebuffers.resize(swapImageCount);
    for (uint32_t i = 0; i < swapImageCount; ++i) {
        VkImageViewCreateInfo viewInfo{};
        viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        viewInfo.image = images[i];
        viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
        viewInfo.format = surfaceFormat.format;
        viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        viewInfo.subresourceRange.baseMipLevel = 0;
        viewInfo.subresourceRange.levelCount = 1;
        viewInfo.subresourceRange.baseArrayLayer = 0;
        viewInfo.subresourceRange.layerCount = 1;
        result = vkCreateImageView(
            device, &viewInfo, nullptr, &imageViews[i]);
        if (result != VK_SUCCESS) {
            LOGE("vkCreateImageView failed: %d", result);
            cleanup();
            return;
        }
    }

    const VkFormat depthFormat = chooseDepthFormat(physicalDevice);
    if (depthFormat == VK_FORMAT_UNDEFINED ||
        !createDepthResources(physicalDevice, device, extent, depthFormat,
                              depthImage, depthMemory, depthView)) {
        LOGE("Unable to create a supported depth attachment");
        cleanup();
        return;
    }

    VkAttachmentDescription attachments[2]{};
    attachments[0].format = surfaceFormat.format;
    attachments[0].samples = VK_SAMPLE_COUNT_1_BIT;
    attachments[0].loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    attachments[0].storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    attachments[0].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    attachments[0].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    attachments[0].initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    attachments[0].finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

    attachments[1].format = depthFormat;
    attachments[1].samples = VK_SAMPLE_COUNT_1_BIT;
    attachments[1].loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    attachments[1].storeOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    attachments[1].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    attachments[1].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    attachments[1].initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    attachments[1].finalLayout =
        VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;

    VkAttachmentReference colorReference{};
    colorReference.attachment = 0;
    colorReference.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
    VkAttachmentReference depthReference{};
    depthReference.attachment = 1;
    depthReference.layout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;

    VkSubpassDescription subpass{};
    subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments = &colorReference;
    subpass.pDepthStencilAttachment = &depthReference;

    VkSubpassDependency dependency{};
    dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
    dependency.dstSubpass = 0;
    dependency.srcStageMask =
        VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT |
        VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
    dependency.dstStageMask =
        VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT |
        VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
    dependency.dstAccessMask =
        VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT |
        VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;

    VkRenderPassCreateInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    renderPassInfo.attachmentCount = 2;
    renderPassInfo.pAttachments = attachments;
    renderPassInfo.subpassCount = 1;
    renderPassInfo.pSubpasses = &subpass;
    renderPassInfo.dependencyCount = 1;
    renderPassInfo.pDependencies = &dependency;
    result = vkCreateRenderPass(
        device, &renderPassInfo, nullptr, &renderPass);
    if (result != VK_SUCCESS) {
        LOGE("vkCreateRenderPass failed: %d", result);
        cleanup();
        return;
    }

    for (uint32_t i = 0; i < swapImageCount; ++i) {
        VkImageView framebufferAttachments[2] = {
            imageViews[i], depthView
        };
        VkFramebufferCreateInfo framebufferInfo{};
        framebufferInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        framebufferInfo.renderPass = renderPass;
        framebufferInfo.attachmentCount = 2;
        framebufferInfo.pAttachments = framebufferAttachments;
        framebufferInfo.width = extent.width;
        framebufferInfo.height = extent.height;
        framebufferInfo.layers = 1;
        result = vkCreateFramebuffer(
            device, &framebufferInfo, nullptr, &framebuffers[i]);
        if (result != VK_SUCCESS) {
            LOGE("vkCreateFramebuffer failed: %d", result);
            cleanup();
            return;
        }
    }

    std::vector<uint8_t> vertexBytes;
    std::vector<uint8_t> fragmentBytes;
    if (!gAssetStore.readRelative("shaders/storm.vert.spv", vertexBytes) ||
        !gAssetStore.readRelative("shaders/storm.frag.spv", fragmentBytes)) {
        LOGE("Compiled Vulkan shaders missing under filesDir/shaders");
        cleanup();
        return;
    }
    vertexShader = createShaderModule(device, vertexBytes);
    fragmentShader = createShaderModule(device, fragmentBytes);
    if (vertexShader == VK_NULL_HANDLE || fragmentShader == VK_NULL_HANDLE) {
        LOGE("Unable to create Vulkan shader modules");
        cleanup();
        return;
    }

    VkPipelineShaderStageCreateInfo shaderStages[2]{};
    shaderStages[0].sType =
        VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    shaderStages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
    shaderStages[0].module = vertexShader;
    shaderStages[0].pName = "main";
    shaderStages[1].sType =
        VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    shaderStages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    shaderStages[1].module = fragmentShader;
    shaderStages[1].pName = "main";

    VkVertexInputBindingDescription binding{};
    binding.binding = 0;
    binding.stride = sizeof(Vertex);
    binding.inputRate = VK_VERTEX_INPUT_RATE_VERTEX;
    VkVertexInputAttributeDescription attributes[2]{};
    attributes[0].location = 0;
    attributes[0].binding = 0;
    attributes[0].format = VK_FORMAT_R32G32B32_SFLOAT;
    attributes[0].offset = offsetof(Vertex, position);
    attributes[1].location = 1;
    attributes[1].binding = 0;
    attributes[1].format = VK_FORMAT_R32G32B32_SFLOAT;
    attributes[1].offset = offsetof(Vertex, normal);

    VkPipelineVertexInputStateCreateInfo vertexInput{};
    vertexInput.sType =
        VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
    vertexInput.vertexBindingDescriptionCount = 1;
    vertexInput.pVertexBindingDescriptions = &binding;
    vertexInput.vertexAttributeDescriptionCount = 2;
    vertexInput.pVertexAttributeDescriptions = attributes;

    VkPipelineInputAssemblyStateCreateInfo inputAssembly{};
    inputAssembly.sType =
        VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
    inputAssembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;

    VkViewport viewport{};
    viewport.x = 0.0f;
    viewport.y = 0.0f;
    viewport.width = static_cast<float>(extent.width);
    viewport.height = static_cast<float>(extent.height);
    viewport.minDepth = 0.0f;
    viewport.maxDepth = 1.0f;
    VkRect2D scissor{};
    scissor.extent = extent;

    VkPipelineViewportStateCreateInfo viewportState{};
    viewportState.sType =
        VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
    viewportState.viewportCount = 1;
    viewportState.pViewports = &viewport;
    viewportState.scissorCount = 1;
    viewportState.pScissors = &scissor;

    VkPipelineRasterizationStateCreateInfo rasterization{};
    rasterization.sType =
        VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
    rasterization.polygonMode = VK_POLYGON_MODE_FILL;
    rasterization.cullMode = VK_CULL_MODE_NONE;
    rasterization.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;
    rasterization.lineWidth = 1.0f;

    VkPipelineMultisampleStateCreateInfo multisampling{};
    multisampling.sType =
        VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
    multisampling.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

    VkPipelineDepthStencilStateCreateInfo depthState{};
    depthState.sType =
        VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO;
    depthState.depthTestEnable = VK_TRUE;
    depthState.depthWriteEnable = VK_TRUE;
    depthState.depthCompareOp = VK_COMPARE_OP_LESS;
    depthState.minDepthBounds = 0.0f;
    depthState.maxDepthBounds = 1.0f;

    VkPipelineColorBlendAttachmentState colorBlendAttachment{};
    colorBlendAttachment.colorWriteMask =
        VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT |
        VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
    VkPipelineColorBlendStateCreateInfo colorBlend{};
    colorBlend.sType =
        VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
    colorBlend.attachmentCount = 1;
    colorBlend.pAttachments = &colorBlendAttachment;

    VkPushConstantRange pushRange{};
    pushRange.stageFlags = VK_SHADER_STAGE_VERTEX_BIT;
    pushRange.offset = 0;
    pushRange.size = sizeof(DrawPushConstants);
    VkPipelineLayoutCreateInfo layoutInfo{};
    layoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
    layoutInfo.pushConstantRangeCount = 1;
    layoutInfo.pPushConstantRanges = &pushRange;
    result = vkCreatePipelineLayout(
        device, &layoutInfo, nullptr, &pipelineLayout);
    if (result != VK_SUCCESS) {
        LOGE("vkCreatePipelineLayout failed: %d", result);
        cleanup();
        return;
    }

    VkGraphicsPipelineCreateInfo pipelineInfo{};
    pipelineInfo.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
    pipelineInfo.stageCount = 2;
    pipelineInfo.pStages = shaderStages;
    pipelineInfo.pVertexInputState = &vertexInput;
    pipelineInfo.pInputAssemblyState = &inputAssembly;
    pipelineInfo.pViewportState = &viewportState;
    pipelineInfo.pRasterizationState = &rasterization;
    pipelineInfo.pMultisampleState = &multisampling;
    pipelineInfo.pDepthStencilState = &depthState;
    pipelineInfo.pColorBlendState = &colorBlend;
    pipelineInfo.layout = pipelineLayout;
    pipelineInfo.renderPass = renderPass;
    pipelineInfo.subpass = 0;
    result = vkCreateGraphicsPipelines(
        device, VK_NULL_HANDLE, 1, &pipelineInfo, nullptr, &graphicsPipeline);
    if (result != VK_SUCCESS) {
        LOGE("vkCreateGraphicsPipelines failed: %d", result);
        cleanup();
        return;
    }
    vkDestroyShaderModule(device, vertexShader, nullptr);
    vkDestroyShaderModule(device, fragmentShader, nullptr);
    vertexShader = VK_NULL_HANDLE;
    fragmentShader = VK_NULL_HANDLE;

    const std::vector<Vertex> vertices = createSphereMesh();
    if (!createVertexBuffer(
            physicalDevice, device, vertices, vertexBuffer, vertexMemory)) {
        LOGE("Unable to create the sandbox vertex buffer");
        cleanup();
        return;
    }
    const uint32_t vertexCount = static_cast<uint32_t>(vertices.size());

    VkCommandPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    poolInfo.queueFamilyIndex = queueFamilyIndex;
    result = vkCreateCommandPool(device, &poolInfo, nullptr, &commandPool);
    if (result != VK_SUCCESS) {
        LOGE("vkCreateCommandPool failed: %d", result);
        cleanup();
        return;
    }

    VkCommandBufferAllocateInfo commandInfo{};
    commandInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    commandInfo.commandPool = commandPool;
    commandInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    commandInfo.commandBufferCount = 1;
    VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
    result = vkAllocateCommandBuffers(
        device, &commandInfo, &commandBuffer);
    if (result != VK_SUCCESS) {
        LOGE("vkAllocateCommandBuffers failed: %d", result);
        cleanup();
        return;
    }

    VkSemaphoreCreateInfo semaphoreInfo{};
    semaphoreInfo.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    if (vkCreateSemaphore(device, &semaphoreInfo, nullptr, &imageAvailable) !=
            VK_SUCCESS ||
        vkCreateSemaphore(device, &semaphoreInfo, nullptr, &renderFinished) !=
            VK_SUCCESS) {
        LOGE("Semaphore creation failed");
        cleanup();
        return;
    }

    VkFenceCreateInfo fenceInfo{};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;
    if (vkCreateFence(device, &fenceInfo, nullptr, &inFlight) != VK_SUCCESS) {
        LOGE("Fence creation failed");
        cleanup();
        return;
    }

    LOGI("Storm 3D Vulkan renderer started: %ux%u, %u mesh vertices",
         extent.width, extent.height, vertexCount);
    gInputSystem.clear();
    gEngineRuntime.start();
    const auto startTime = std::chrono::steady_clock::now();

    while (!gStopRenderer.load()) {
        const auto now = std::chrono::steady_clock::now();
        const auto elapsed = now.time_since_epoch();
        const int64_t nowNs =
            std::chrono::duration_cast<std::chrono::nanoseconds>(elapsed).count();
        const storm::InputFrame input = gInputSystem.snapshot();
        gEngineRuntime.advance(nowNs, input);
        const storm::Vec3 actor = gEngineRuntime.actorPosition();
        const float seconds = std::chrono::duration<float>(
            now - startTime).count();

        result = vkWaitForFences(
            device, 1, &inFlight, VK_TRUE, UINT64_MAX);
        if (result != VK_SUCCESS) break;
        vkResetFences(device, 1, &inFlight);

        uint32_t imageIndex = 0;
        result = vkAcquireNextImageKHR(
            device, swapchain, UINT64_MAX, imageAvailable,
            VK_NULL_HANDLE, &imageIndex);
        if (result == VK_ERROR_OUT_OF_DATE_KHR ||
            result == VK_ERROR_SURFACE_LOST_KHR) break;
        if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) break;

        result = vkResetCommandPool(device, commandPool, 0);
        if (result != VK_SUCCESS) break;
        VkCommandBufferBeginInfo beginInfo{};
        beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
        if (vkBeginCommandBuffer(commandBuffer, &beginInfo) != VK_SUCCESS)
            break;

        VkClearValue clearValues[2]{};
        clearValues[0].color.float32[0] = 0.025f;
        clearValues[0].color.float32[1] = 0.035f;
        clearValues[0].color.float32[2] = 0.060f;
        clearValues[0].color.float32[3] = 1.0f;
        clearValues[1].depthStencil.depth = 1.0f;
        clearValues[1].depthStencil.stencil = 0;

        VkRenderPassBeginInfo renderBegin{};
        renderBegin.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
        renderBegin.renderPass = renderPass;
        renderBegin.framebuffer = framebuffers[imageIndex];
        renderBegin.renderArea.extent = extent;
        renderBegin.clearValueCount = 2;
        renderBegin.pClearValues = clearValues;
        vkCmdBeginRenderPass(
            commandBuffer, &renderBegin, VK_SUBPASS_CONTENTS_INLINE);
        vkCmdBindPipeline(
            commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);

        const std::array<float, 3> eye{
            actor.x, actor.y + 2.65f, actor.z + 6.5f};
        const std::array<float, 3> target{
            actor.x, actor.y + 0.95f, actor.z};
        const Mat4 view = lookAt(eye, target);
        const Mat4 projection = perspective(
            0.82f,
            static_cast<float>(extent.width) /
                static_cast<float>(std::max(1u, extent.height)),
            0.10f, 40.0f);
        const Mat4 viewProjection = multiply(projection, view);

        drawSandboxScene(
            commandBuffer, pipelineLayout, vertexBuffer, vertexCount,
            viewProjection, actor, input, seconds);

        vkCmdEndRenderPass(commandBuffer);
        if (vkEndCommandBuffer(commandBuffer) != VK_SUCCESS) break;

        const VkPipelineStageFlags waitStage =
            VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        VkSubmitInfo submitInfo{};
        submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
        submitInfo.waitSemaphoreCount = 1;
        submitInfo.pWaitSemaphores = &imageAvailable;
        submitInfo.pWaitDstStageMask = &waitStage;
        submitInfo.commandBufferCount = 1;
        submitInfo.pCommandBuffers = &commandBuffer;
        submitInfo.signalSemaphoreCount = 1;
        submitInfo.pSignalSemaphores = &renderFinished;
        if (vkQueueSubmit(
                graphicsQueue, 1, &submitInfo, inFlight) != VK_SUCCESS) {
            break;
        }

        VkPresentInfoKHR presentInfo{};
        presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
        presentInfo.waitSemaphoreCount = 1;
        presentInfo.pWaitSemaphores = &renderFinished;
        presentInfo.swapchainCount = 1;
        presentInfo.pSwapchains = &swapchain;
        presentInfo.pImageIndices = &imageIndex;
        result = vkQueuePresentKHR(presentQueue, &presentInfo);
        vkQueueWaitIdle(presentQueue);
        if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) break;
    }

    LOGI("Storm 3D Vulkan renderer stopped");
    cleanup();
}

void stopRenderer() {
    std::thread threadToJoin;
    {
        std::lock_guard<std::mutex> lock(gRendererMutex);
        gStopRenderer.store(true);
        if (gRendererThread.joinable())
            threadToJoin = std::move(gRendererThread);
    }
    if (threadToJoin.joinable()) threadToJoin.join();
}

}  // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_stormrevolution_launcher_StormEngine_nativeGetVulkanReport(
    JNIEnv* environment, jobject /* caller */) {
    const std::string report = queryVulkanDevices();
    return environment->NewStringUTF(report.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_stormrevolution_launcher_StormEngine_nativeStartRenderer(
    JNIEnv* environment, jobject /* caller */, jobject javaSurface) {
    stopRenderer();
    ANativeWindow* window = ANativeWindow_fromSurface(environment, javaSurface);
    if (window == nullptr) {
        LOGE("ANativeWindow_fromSurface returned null");
        return;
    }
    {
        std::lock_guard<std::mutex> lock(gRendererMutex);
        gStopRenderer.store(false);
        gRendererThread = std::thread(renderLoop, window);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_stormrevolution_launcher_StormEngine_nativeStopRenderer(
    JNIEnv* /* environment */, jobject /* caller */) {
    stopRenderer();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_stormrevolution_launcher_StormEngine_nativeLoadAsset(
    JNIEnv* env, jobject /* caller */, jstring relativePath) {
    if (relativePath == nullptr) return JNI_FALSE;
    const char* chars = env->GetStringUTFChars(relativePath, nullptr);
    if (chars == nullptr) return JNI_FALSE;
    const bool loaded = gAssetStore.loadRelative(chars);
    env->ReleaseStringUTFChars(relativePath, chars);
    return loaded ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_stormrevolution_launcher_StormEngine_nativeGetEngineReport(
    JNIEnv* env, jobject /* caller */) {
    const std::string report =
        gEngineRuntime.report() + "\n" + gAssetStore.report();
    return env->NewStringUTF(report.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_stormrevolution_launcher_StormEngine_nativeSendTouch(
    JNIEnv* /* env */, jobject /* caller */,
    jint pointerId, jfloat x, jfloat y, jint action) {
    gInputSystem.pushTouch(pointerId, x, y, action);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_stormrevolution_launcher_StormEngine_nativeSetAssetRoot(
    JNIEnv* env, jobject /* caller */, jstring rootPath) {
    if (rootPath == nullptr) return;
    const char* chars = env->GetStringUTFChars(rootPath, nullptr);
    if (chars == nullptr) return;
    gAssetStore.setRoot(chars);
    env->ReleaseStringUTFChars(rootPath, chars);
}

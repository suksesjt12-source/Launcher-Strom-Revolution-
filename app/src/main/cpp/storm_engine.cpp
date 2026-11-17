#define VK_USE_PLATFORM_ANDROID_KHR

#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <jni.h>
#include <vulkan/vulkan.h>

#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#define LOG_TAG "StormVulkan"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex gRendererMutex;
std::atomic<bool> gStopRenderer{false};
std::thread gRendererThread;

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
    if (enumerateVersion != nullptr) {
        if (enumerateVersion(&loaderVersion) != VK_SUCCESS) {
            loaderVersion = VK_API_VERSION_1_0;
        }
    }

    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "Storm Revolution Launcher";
    appInfo.applicationVersion = VK_MAKE_VERSION(0, 4, 0);
    appInfo.pEngineName = "Storm Native Vulkan";
    appInfo.engineVersion = VK_MAKE_VERSION(0, 4, 0);
    appInfo.apiVersion = VK_API_VERSION_1_1;

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;

    VkInstance instance = VK_NULL_HANDLE;
    const VkResult createResult = vkCreateInstance(&createInfo, nullptr, &instance);

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
    VkResult result = vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr);
    if (result != VK_SUCCESS || deviceCount == 0) {
        report += "Vulkan physical device: tidak ditemukan\nGPU gate: tidak dapat diperiksa";
        vkDestroyInstance(instance, nullptr);
        return report;
    }

    std::vector<VkPhysicalDevice> devices(deviceCount);
    result = vkEnumeratePhysicalDevices(instance, &deviceCount, devices.data());
    if (result != VK_SUCCESS) {
        report += "Gagal membaca daftar GPU\nGPU gate: tidak dapat diperiksa";
        vkDestroyInstance(instance, nullptr);
        return report;
    }

    report += "GPU Vulkan terdeteksi: " + std::to_string(deviceCount) + "\n";
    for (uint32_t i = 0; i < deviceCount; ++i) {
        VkPhysicalDeviceProperties properties{};
        vkGetPhysicalDeviceProperties(devices[i], &properties);

        const bool api12 = properties.apiVersion >= VK_API_VERSION_1_2;
        const bool bdaExtension =
            hasDeviceExtension(devices[i], "VK_KHR_buffer_device_address");

        bool bdaQueryable = false;
        bool bdaSupported = false;
        if (properties.apiVersion >= VK_API_VERSION_1_1 &&
            (api12 || bdaExtension)) {
            VkPhysicalDeviceBufferDeviceAddressFeatures bda{};
            bda.sType =
                VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BUFFER_DEVICE_ADDRESS_FEATURES;
            VkPhysicalDeviceFeatures2 features{};
            features.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
            features.pNext = &bda;
            vkGetPhysicalDeviceFeatures2(devices[i], &features);
            bdaQueryable = true;
            bdaSupported = bda.bufferDeviceAddress == VK_TRUE;
        }

        report += "- GPU: ";
        report += properties.deviceName;
        report += "\n  Vulkan API: " +
            std::to_string(VK_API_VERSION_MAJOR(properties.apiVersion)) + "." +
            std::to_string(VK_API_VERSION_MINOR(properties.apiVersion));
        report += "\n  Buffer device address: ";
        report += !bdaQueryable ? "tidak tersedia / tidak dapat diperiksa" :
            (bdaSupported ? "didukung" : "tidak didukung");
        report += "\n  Pemeriksaan fitur: ";
        report += bdaSupported ? "LOLOS" : "BELUM LOLOS";
        report += "\n";
    }

    report += "Vulkan instance: berhasil\n";
    report += "Pemeriksaan GPU bukan bukti game dapat berjalan.";
    vkDestroyInstance(instance, nullptr);
    return report;
}

uint32_t chooseMemoryType(
    VkPhysicalDevice physicalDevice,
    uint32_t typeBits,
    VkMemoryPropertyFlags properties) {
    VkPhysicalDeviceMemoryProperties memoryProperties{};
    vkGetPhysicalDeviceMemoryProperties(physicalDevice, &memoryProperties);
    for (uint32_t i = 0; i < memoryProperties.memoryTypeCount; ++i) {
        if ((typeBits & (1u << i)) != 0 &&
            (memoryProperties.memoryTypes[i].propertyFlags & properties) == properties) {
            return i;
        }
    }
    return UINT32_MAX;
}

void renderLoop(ANativeWindow* window) {
    if (window == nullptr) return;

    VkInstance instance = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    VkRenderPass renderPass = VK_NULL_HANDLE;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    VkSemaphore imageAvailable = VK_NULL_HANDLE;
    VkSemaphore renderFinished = VK_NULL_HANDLE;
    VkFence inFlight = VK_NULL_HANDLE;
    std::vector<VkImageView> imageViews;
    std::vector<VkFramebuffer> framebuffers;
    std::vector<VkCommandBuffer> commandBuffers;
    VkQueue graphicsQueue = VK_NULL_HANDLE;
    VkQueue presentQueue = VK_NULL_HANDLE;

    auto cleanup = [&]() {
        if (device != VK_NULL_HANDLE) vkDeviceWaitIdle(device);
        if (device != VK_NULL_HANDLE && inFlight != VK_NULL_HANDLE)
            vkDestroyFence(device, inFlight, nullptr);
        if (device != VK_NULL_HANDLE && imageAvailable != VK_NULL_HANDLE)
            vkDestroySemaphore(device, imageAvailable, nullptr);
        if (device != VK_NULL_HANDLE && renderFinished != VK_NULL_HANDLE)
            vkDestroySemaphore(device, renderFinished, nullptr);
        if (device != VK_NULL_HANDLE && commandPool != VK_NULL_HANDLE)
            vkDestroyCommandPool(device, commandPool, nullptr);
        if (device != VK_NULL_HANDLE) {
            for (auto framebuffer : framebuffers)
                vkDestroyFramebuffer(device, framebuffer, nullptr);
            if (renderPass != VK_NULL_HANDLE)
                vkDestroyRenderPass(device, renderPass, nullptr);
            for (auto view : imageViews)
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
    appInfo.pApplicationName = "Storm Native Preview";
    appInfo.applicationVersion = VK_MAKE_VERSION(0, 4, 0);
    appInfo.pEngineName = "Storm Vulkan Renderer";
    appInfo.engineVersion = VK_MAKE_VERSION(0, 4, 0);
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
    result = vkEnumeratePhysicalDevices(instance, &physicalCount, physicalDevices.data());
    if (result != VK_SUCCESS) {
        LOGE("vkEnumeratePhysicalDevices failed: %d", result);
        cleanup();
        return;
    }

    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    uint32_t queueFamilyIndex = UINT32_MAX;
    for (VkPhysicalDevice candidate : physicalDevices) {
        if (!hasDeviceExtension(candidate, VK_KHR_SWAPCHAIN_EXTENSION_NAME)) continue;
        uint32_t familyCount = 0;
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, &familyCount, nullptr);
        std::vector<VkQueueFamilyProperties> families(familyCount);
        vkGetPhysicalDeviceQueueFamilyProperties(candidate, &familyCount, families.data());
        for (uint32_t i = 0; i < familyCount; ++i) {
            VkBool32 presentSupported = VK_FALSE;
            VkResult supportResult =
                vkGetPhysicalDeviceSurfaceSupportKHR(candidate, i, surface, &presentSupported);
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
        (capabilities.supportedUsageFlags & VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) == 0) {
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
        const uint32_t width = static_cast<uint32_t>(ANativeWindow_getWidth(window));
        const uint32_t height = static_cast<uint32_t>(ANativeWindow_getHeight(window));
        extent.width = width < capabilities.minImageExtent.width ?
            capabilities.minImageExtent.width :
            (width > capabilities.maxImageExtent.width ?
                capabilities.maxImageExtent.width : width);
        extent.height = height < capabilities.minImageExtent.height ?
            capabilities.minImageExtent.height :
            (height > capabilities.maxImageExtent.height ?
                capabilities.maxImageExtent.height : height);
    }

    uint32_t imageCount = capabilities.minImageCount + 1;
    if (capabilities.maxImageCount > 0 && imageCount > capabilities.maxImageCount)
        imageCount = capabilities.maxImageCount;

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
        (capabilities.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR) ?
        VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR :
        static_cast<VkCompositeAlphaFlagBitsKHR>(
            capabilities.supportedCompositeAlpha &
            (~capabilities.supportedCompositeAlpha + 1));
    swapchainInfo.presentMode = VK_PRESENT_MODE_FIFO_KHR;
    swapchainInfo.clipped = VK_TRUE;

    result = vkCreateSwapchainKHR(device, &swapchainInfo, nullptr, &swapchain);
    if (result != VK_SUCCESS) {
        LOGE("vkCreateSwapchainKHR failed: %d", result);
        cleanup();
        return;
    }

    uint32_t swapImageCount = 0;
    vkGetSwapchainImagesKHR(device, swapchain, &swapImageCount, nullptr);
    std::vector<VkImage> images(swapImageCount);
    vkGetSwapchainImagesKHR(device, swapchain, &swapImageCount, images.data());

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
        result = vkCreateImageView(device, &viewInfo, nullptr, &imageViews[i]);
        if (result != VK_SUCCESS) {
            LOGE("vkCreateImageView failed: %d", result);
            cleanup();
            return;
        }
    }

    VkAttachmentDescription attachment{};
    attachment.format = surfaceFormat.format;
    attachment.samples = VK_SAMPLE_COUNT_1_BIT;
    attachment.loadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    attachment.storeOp = VK_ATTACHMENT_STORE_OP_STORE;
    attachment.stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    attachment.stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    attachment.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    attachment.finalLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;

    VkAttachmentReference colorReference{};
    colorReference.attachment = 0;
    colorReference.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

    VkSubpassDescription subpass{};
    subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments = &colorReference;

    VkSubpassDependency dependency{};
    dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
    dependency.dstSubpass = 0;
    dependency.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dependency.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

    VkRenderPassCreateInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    renderPassInfo.attachmentCount = 1;
    renderPassInfo.pAttachments = &attachment;
    renderPassInfo.subpassCount = 1;
    renderPassInfo.pSubpasses = &subpass;
    renderPassInfo.dependencyCount = 1;
    renderPassInfo.pDependencies = &dependency;

    result = vkCreateRenderPass(device, &renderPassInfo, nullptr, &renderPass);
    if (result != VK_SUCCESS) {
        LOGE("vkCreateRenderPass failed: %d", result);
        cleanup();
        return;
    }

    for (uint32_t i = 0; i < swapImageCount; ++i) {
        VkFramebufferCreateInfo framebufferInfo{};
        framebufferInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        framebufferInfo.renderPass = renderPass;
        framebufferInfo.attachmentCount = 1;
        framebufferInfo.pAttachments = &imageViews[i];
        framebufferInfo.width = extent.width;
        framebufferInfo.height = extent.height;
        framebufferInfo.layers = 1;
        result = vkCreateFramebuffer(device, &framebufferInfo, nullptr, &framebuffers[i]);
        if (result != VK_SUCCESS) {
            LOGE("vkCreateFramebuffer failed: %d", result);
            cleanup();
            return;
        }
    }

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
    result = vkAllocateCommandBuffers(device, &commandInfo, &commandBuffer);
    if (result != VK_SUCCESS) {
        LOGE("vkAllocateCommandBuffers failed: %d", result);
        cleanup();
        return;
    }
    commandBuffers.push_back(commandBuffer);

    VkSemaphoreCreateInfo semaphoreInfo{};
    semaphoreInfo.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    if (vkCreateSemaphore(device, &semaphoreInfo, nullptr, &imageAvailable) != VK_SUCCESS ||
        vkCreateSemaphore(device, &semaphoreInfo, nullptr, &renderFinished) != VK_SUCCESS) {
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

    LOGI("Native Vulkan swapchain started: %ux%u", extent.width, extent.height);

    while (!gStopRenderer.load()) {
        result = vkWaitForFences(device, 1, &inFlight, VK_TRUE, UINT64_MAX);
        if (result != VK_SUCCESS) break;
        vkResetFences(device, 1, &inFlight);

        uint32_t imageIndex = 0;
        result = vkAcquireNextImageKHR(
            device, swapchain, UINT64_MAX, imageAvailable, VK_NULL_HANDLE, &imageIndex);
        if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_ERROR_SURFACE_LOST_KHR)
            break;
        if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) break;

        vkResetCommandBuffer(commandBuffer, 0);
        VkCommandBufferBeginInfo beginInfo{};
        beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
        if (vkBeginCommandBuffer(commandBuffer, &beginInfo) != VK_SUCCESS) break;

        VkClearValue clearValue{};
        clearValue.color.float32[0] = 0.035f;
        clearValue.color.float32[1] = 0.045f;
        clearValue.color.float32[2] = 0.075f;
        clearValue.color.float32[3] = 1.0f;

        VkRenderPassBeginInfo renderBegin{};
        renderBegin.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
        renderBegin.renderPass = renderPass;
        renderBegin.framebuffer = framebuffers[imageIndex];
        renderBegin.renderArea.extent = extent;
        renderBegin.clearValueCount = 1;
        renderBegin.pClearValues = &clearValue;
        vkCmdBeginRenderPass(commandBuffer, &renderBegin, VK_SUBPASS_CONTENTS_INLINE);
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

        if (vkQueueSubmit(graphicsQueue, 1, &submitInfo, inFlight) != VK_SUCCESS)
            break;

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

    LOGI("Native Vulkan surface renderer stopped");
    cleanup();
}

void stopRenderer() {
    std::thread threadToJoin;
    {
        std::lock_guard<std::mutex> lock(gRendererMutex);
        gStopRenderer.store(true);
        if (gRendererThread.joinable()) {
            threadToJoin = std::move(gRendererThread);
        }
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

#include <jni.h>
#include <vulkan/vulkan.h>

#include <sstream>
#include <string>
#include <vector>

namespace {

std::string queryVulkanDevices() {
    uint32_t loaderVersion = VK_API_VERSION_1_0;
    const auto enumerateVersion =
        reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
            vkGetInstanceProcAddr(VK_NULL_HANDLE, "vkEnumerateInstanceVersion"));

    if (enumerateVersion != nullptr) {
        const VkResult versionResult = enumerateVersion(&loaderVersion);
        if (versionResult != VK_SUCCESS) {
            loaderVersion = VK_API_VERSION_1_0;
        }
    }

    VkApplicationInfo applicationInfo{};
    applicationInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    applicationInfo.pApplicationName = "Storm Revolution Launcher";
    applicationInfo.applicationVersion = VK_MAKE_VERSION(0, 1, 0);
    applicationInfo.pEngineName = "Storm Native Bootstrap";
    applicationInfo.engineVersion = VK_MAKE_VERSION(0, 1, 0);
    applicationInfo.apiVersion = VK_API_VERSION_1_0;

    VkInstanceCreateInfo instanceInfo{};
    instanceInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    instanceInfo.pApplicationInfo = &applicationInfo;

    VkInstance instance = VK_NULL_HANDLE;
    const VkResult createResult = vkCreateInstance(&instanceInfo, nullptr, &instance);

    std::ostringstream report;
    report << "Vulkan loader: "
           << VK_API_VERSION_MAJOR(loaderVersion) << "."
           << VK_API_VERSION_MINOR(loaderVersion) << "\n";

    if (createResult != VK_SUCCESS || instance == VK_NULL_HANDLE) {
        report << "Vulkan instance: gagal (VkResult "
               << static_cast<int>(createResult) << ")\n";
        report << "Renderer native: belum siap";
        return report.str();
    }

    uint32_t deviceCount = 0;
    VkResult deviceResult = vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr);

    if (deviceResult != VK_SUCCESS || deviceCount == 0) {
        report << "Vulkan physical device: tidak ditemukan\n";
        report << "Renderer native: belum siap";
        vkDestroyInstance(instance, nullptr);
        return report.str();
    }

    std::vector<VkPhysicalDevice> devices(deviceCount);
    deviceResult = vkEnumeratePhysicalDevices(instance, &deviceCount, devices.data());

    if (deviceResult != VK_SUCCESS) {
        report << "Gagal membaca daftar GPU (VkResult "
               << static_cast<int>(deviceResult) << ")\n";
        report << "Renderer native: belum siap";
        vkDestroyInstance(instance, nullptr);
        return report.str();
    }

    report << "GPU Vulkan terdeteksi: " << deviceCount << "\n";
    for (uint32_t index = 0; index < deviceCount; ++index) {
        VkPhysicalDeviceProperties properties{};
        vkGetPhysicalDeviceProperties(devices[index], &properties);

        report << "- " << properties.deviceName
               << " (API "
               << VK_API_VERSION_MAJOR(properties.apiVersion) << "."
               << VK_API_VERSION_MINOR(properties.apiVersion) << ")\n";
    }

    report << "Vulkan instance: berhasil\n";
    report << "Renderer game: belum diintegrasikan";
    vkDestroyInstance(instance, nullptr);
    return report.str();
}

}  // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_stormrevolution_launcher_StormEngine_nativeGetVulkanReport(
    JNIEnv* environment,
    jobject /* caller */) {
    const std::string report = queryVulkanDevices();
    return environment->NewStringUTF(report.c_str());
}

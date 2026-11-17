#include <jni.h>
#include <vulkan/vulkan.h>

#include <sstream>
#include <string>
#include <vector>

namespace {

bool hasDeviceExtension(VkPhysicalDevice device, const char* requestedName) {
    uint32_t count = 0;
    VkResult result = vkEnumerateDeviceExtensionProperties(
        device, nullptr, &count, nullptr);
    if (result != VK_SUCCESS || count == 0) {
        return false;
    }

    std::vector<VkExtensionProperties> extensions(count);
    result = vkEnumerateDeviceExtensionProperties(
        device, nullptr, &count, extensions.data());
    if (result != VK_SUCCESS) {
        return false;
    }

    for (const auto& extension : extensions) {
        if (std::string(extension.extensionName) == requestedName) {
            return true;
        }
    }
    return false;
}

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
    applicationInfo.applicationVersion = VK_MAKE_VERSION(0, 3, 0);
    applicationInfo.pEngineName = "Storm Native Diagnostics";
    applicationInfo.engineVersion = VK_MAKE_VERSION(0, 3, 0);
    applicationInfo.apiVersion = VK_API_VERSION_1_1;

    VkInstanceCreateInfo instanceInfo{};
    instanceInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    instanceInfo.pApplicationInfo = &applicationInfo;

    VkInstance instance = VK_NULL_HANDLE;
    const VkResult createResult =
        vkCreateInstance(&instanceInfo, nullptr, &instance);

    std::ostringstream report;
    report << "Vulkan loader: "
           << VK_API_VERSION_MAJOR(loaderVersion) << "."
           << VK_API_VERSION_MINOR(loaderVersion) << "\n";

    if (createResult != VK_SUCCESS || instance == VK_NULL_HANDLE) {
        report << "Vulkan instance: gagal (VkResult "
               << static_cast<int>(createResult) << ")\n";
        report << "GPU gate: tidak dapat diperiksa";
        return report.str();
    }

    uint32_t deviceCount = 0;
    VkResult deviceResult =
        vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr);

    if (deviceResult != VK_SUCCESS || deviceCount == 0) {
        report << "Vulkan physical device: tidak ditemukan\n";
        report << "GPU gate: tidak dapat diperiksa";
        vkDestroyInstance(instance, nullptr);
        return report.str();
    }

    std::vector<VkPhysicalDevice> devices(deviceCount);
    deviceResult = vkEnumeratePhysicalDevices(
        instance, &deviceCount, devices.data());

    if (deviceResult != VK_SUCCESS) {
        report << "Gagal membaca daftar GPU (VkResult "
               << static_cast<int>(deviceResult) << ")\n";
        report << "GPU gate: tidak dapat diperiksa";
        vkDestroyInstance(instance, nullptr);
        return report.str();
    }

    report << "GPU Vulkan terdeteksi: " << deviceCount << "\n";
    for (uint32_t index = 0; index < deviceCount; ++index) {
        VkPhysicalDeviceProperties properties{};
        vkGetPhysicalDeviceProperties(devices[index], &properties);

        const bool api12 =
            properties.apiVersion >= VK_API_VERSION_1_2;
        const bool bdaExtension =
            hasDeviceExtension(devices[index],
                               "VK_KHR_buffer_device_address");

        bool bdaSupported = false;
        bool bdaQueryable = false;

        // Vulkan 1.1 exposes vkGetPhysicalDeviceFeatures2. Buffer device
        // address is core in 1.2 or available through the KHR extension.
        if (properties.apiVersion >= VK_API_VERSION_1_1 &&
            (api12 || bdaExtension)) {
            VkPhysicalDeviceBufferDeviceAddressFeatures bdaFeatures{};
            bdaFeatures.sType =
                VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BUFFER_DEVICE_ADDRESS_FEATURES;

            VkPhysicalDeviceFeatures2 features{};
            features.sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2;
            features.pNext = &bdaFeatures;

            vkGetPhysicalDeviceFeatures2(devices[index], &features);
            bdaQueryable = true;
            bdaSupported =
                bdaFeatures.bufferDeviceAddress == VK_TRUE;
        }

        report << "- GPU: " << properties.deviceName << "\n";
        report << "  Vulkan API: "
               << VK_API_VERSION_MAJOR(properties.apiVersion) << "."
               << VK_API_VERSION_MINOR(properties.apiVersion) << "\n";
        report << "  Buffer device address: ";
        if (!bdaQueryable) {
            report << "tidak tersedia / tidak dapat diperiksa\n";
        } else {
            report << (bdaSupported ? "didukung\n" : "tidak didukung\n");
        }
        report << "  Persyaratan GPU port: "
               << (bdaSupported ? "LOLOS pemeriksaan fitur" :
                                  "BELUM LOLOS pemeriksaan fitur")
               << "\n";
    }

    report << "Vulkan instance: berhasil\n";
    report << "Catatan: pemeriksaan GPU bukan bukti game dapat berjalan.";
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

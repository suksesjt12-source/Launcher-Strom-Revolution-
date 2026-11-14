#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <elf.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/sysinfo.h>
#include <sys/utsname.h>
#include <unistd.h>

#include <cerrno>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>

#define LOG_TAG "StormRuntimeBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// Minimal Vulkan types for dlopen probing without requiring vulkan_core.h at compile time
using PFN_vkEnumerateInstanceVersion = int32_t (*)(uint32_t* pApiVersion);

struct ElfProbeResult {
    bool valid = false;
    int bitness = 0;      // 32 or 64
    uint16_t machine = 0; // EM_AARCH64=183, EM_ARM=40, EM_X86_64=62, EM_386=3
    bool executable = false;
};

ElfProbeResult probeElfBinary(const char* path) {
    ElfProbeResult result{};
    if (!path) return result;

    struct stat st{};
    if (stat(path, &st) != 0 || !S_ISREG(st.st_mode)) {
        return result;
    }
    result.executable = (access(path, X_OK) == 0);

    int fd = open(path, O_RDONLY);
    if (fd < 0) return result;

    uint8_t header[64]{};
    ssize_t n = read(fd, header, sizeof(header));
    close(fd);
    if (n < 20) return result;

    if (header[0] != ELFMAG0 || header[1] != ELFMAG1 ||
        header[2] != ELFMAG2 || header[3] != ELFMAG3) {
        return result;
    }

    result.valid = true;
    result.bitness = (header[EI_CLASS] == ELFCLASS64) ? 64 : (header[EI_CLASS] == ELFCLASS32 ? 32 : 0);
    result.machine = static_cast<uint16_t>(header[18]) | (static_cast<uint16_t>(header[19]) << 8);
    return result;
}

std::string readProcCpuHardware() {
    std::ifstream file("/proc/cpuinfo");
    if (!file.is_open()) return "Unknown";
    std::string line;
    while (std::getline(file, line)) {
        if (line.rfind("Hardware", 0) == 0 || line.rfind("model name", 0) == 0) {
            auto colon = line.find(':');
            if (colon != std::string::npos) {
                std::string val = line.substr(colon + 1);
                val.erase(0, val.find_first_not_of(" \t"));
                return val;
            }
        }
    }
    return "ARM64 SoC";
}

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeProbeEnvironment(JNIEnv* env, jobject /*thiz*/) {
    struct utsname uts{};
    uname(&uts);

    struct sysinfo si{};
    sysinfo(&si);
    uint64_t totalRamMb = (static_cast<uint64_t>(si.totalram) * si.mem_unit) / (1024ULL * 1024ULL);
    uint64_t freeRamMb = (static_cast<uint64_t>(si.freeram) * si.mem_unit) / (1024ULL * 1024ULL);
    long pageSize = sysconf(_SC_PAGESIZE);
    long cpuCores = sysconf(_SC_NPROCESSORS_ONLN);

    // Probe libvulkan.so dynamically
    bool vulkanLoaderFound = false;
    uint32_t vulkanApiVersion = 0;
    void* vkLib = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (vkLib) {
        vulkanLoaderFound = true;
        auto enumVer = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
            dlsym(vkLib, "vkEnumerateInstanceVersion")
        );
        if (enumVer) {
            uint32_t ver = 0;
            if (enumVer(&ver) == 0) {
                vulkanApiVersion = ver;
            }
        } else {
            vulkanApiVersion = (1U << 22); // Vulkan 1.0 baseline
        }
        dlclose(vkLib);
    }

    uint32_t vkMajor = (vulkanApiVersion >> 22) & 0x7F;
    uint32_t vkMinor = (vulkanApiVersion >> 12) & 0x3FF;
    uint32_t vkPatch = vulkanApiVersion & 0xFFF;

    std::ostringstream json;
    json << "{"
         << "\"abi\":\"" << uts.machine << "\","
         << "\"kernel\":\"" << uts.release << "\","
         << "\"cpuHardware\":\"" << readProcCpuHardware() << "\","
         << "\"cpuCores\":" << cpuCores << ","
         << "\"pageSize\":" << pageSize << ","
         << "\"totalRamMb\":" << totalRamMb << ","
         << "\"freeRamMb\":" << freeRamMb << ","
         << "\"vulkanLoaderFound\":" << (vulkanLoaderFound ? "true" : "false") << ","
         << "\"vulkanVersion\":\"" << vkMajor << "." << vkMinor << "." << vkPatch << "\""
         << "}";

    LOGI("Probed native environment: %s", json.str().c_str());
    return env->NewStringUTF(json.str().c_str());
}

JNIEXPORT jstring JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeInspectElfBinary(
    JNIEnv* env, jobject /*thiz*/, jstring jPath) {
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    ElfProbeResult res = probeElfBinary(path);
    env->ReleaseStringUTFChars(jPath, path);

    std::ostringstream json;
    json << "{"
         << "\"valid\":" << (res.valid ? "true" : "false") << ","
         << "\"bitness\":" << res.bitness << ","
         << "\"machine\":" << res.machine << ","
         << "\"executable\":" << (res.executable ? "true" : "false")
         << "}";
    return env->NewStringUTF(json.str().c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeChmodExecutable(
    JNIEnv* env, jobject /*thiz*/, jstring jPath) {
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    int rc = chmod(path, 0755);
    if (rc != 0) {
        LOGE("chmod(0755) failed for %s: %s", path, strerror(errno));
    }
    env->ReleaseStringUTFChars(jPath, path);
    return rc == 0 ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"

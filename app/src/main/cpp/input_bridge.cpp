#include <jni.h>
#include <android/log.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#include <cstdint>
#include <cstring>

#define LOG_TAG "StormInputBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

#pragma pack(push, 1)
struct StormGamepadPacket {
    uint32_t magic;       // 0x5354524D ('STRM')
    uint16_t buttons;     // XInput bitmask
    uint8_t leftTrigger;  // 0..255
    uint8_t rightTrigger; // 0..255
    int16_t thumbLX;      // -32768..32767
    int16_t thumbLY;      // -32768..32767
    int16_t thumbRX;      // -32768..32767
    int16_t thumbRY;      // -32768..32767
};
#pragma pack(pop)

int g_inputSocketFd = -1;

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeSendGamepadState(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring jSocketPath,
    jint buttons,
    jint leftTrigger,
    jint rightTrigger,
    jint thumbLX,
    jint thumbLY,
    jint thumbRX,
    jint thumbRY) {

    const char* socketPath = env->GetStringUTFChars(jSocketPath, nullptr);
    if (!socketPath || socketPath[0] == '\0') {
        if (socketPath) env->ReleaseStringUTFChars(jSocketPath, socketPath);
        return JNI_FALSE;
    }

    if (g_inputSocketFd < 0) {
        g_inputSocketFd = socket(AF_UNIX, SOCK_DGRAM | SOCK_NONBLOCK, 0);
        if (g_inputSocketFd < 0) {
            env->ReleaseStringUTFChars(jSocketPath, socketPath);
            return JNI_FALSE;
        }
    }

    struct sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    if (std::strlen(socketPath) >= sizeof(addr.sun_path)) {
        env->ReleaseStringUTFChars(jSocketPath, socketPath);
        return JNI_FALSE;
    }
    std::strncpy(addr.sun_path, socketPath, sizeof(addr.sun_path) - 1);
    env->ReleaseStringUTFChars(jSocketPath, socketPath);

    StormGamepadPacket pkt{};
    pkt.magic = 0x5354524DU;
    pkt.buttons = static_cast<uint16_t>(buttons & 0xFFFF);
    pkt.leftTrigger = static_cast<uint8_t>(leftTrigger & 0xFF);
    pkt.rightTrigger = static_cast<uint8_t>(rightTrigger & 0xFF);
    pkt.thumbLX = static_cast<int16_t>(thumbLX);
    pkt.thumbLY = static_cast<int16_t>(thumbLY);
    pkt.thumbRX = static_cast<int16_t>(thumbRX);
    pkt.thumbRY = static_cast<int16_t>(thumbRY);

    ssize_t sent = sendto(
        g_inputSocketFd,
        &pkt,
        sizeof(pkt),
        MSG_DONTWAIT,
        reinterpret_cast<struct sockaddr*>(&addr),
        sizeof(addr)
    );
    return sent == sizeof(pkt) ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"

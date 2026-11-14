#include <jni.h>
#include <android/log.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include <cerrno>
#include <cstring>
#include <string>
#include <vector>

#define LOG_TAG "StormProcessBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::vector<std::string> toStringVector(JNIEnv* env, jobjectArray arr) {
    std::vector<std::string> out;
    if (!arr) return out;
    jsize len = env->GetArrayLength(arr);
    out.reserve(static_cast<size_t>(len));
    for (jsize i = 0; i < len; ++i) {
        auto jStr = static_cast<jstring>(env->GetObjectArrayElement(arr, i));
        if (jStr) {
            const char* cStr = env->GetStringUTFChars(jStr, nullptr);
            if (cStr) {
                out.emplace_back(cStr);
                env->ReleaseStringUTFChars(jStr, cStr);
            }
            env->DeleteLocalRef(jStr);
        }
    }
    return out;
}

} // namespace

extern "C" {

/**
 * Forks and execs a Wine / Box64 / Box86 process cleanly without shell injection.
 * Returns intArrayOf(pid, stdoutFd, stderrFd), or null on failure.
 */
JNIEXPORT jintArray JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeSpawnProcess(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring jExecutable,
    jobjectArray jArgs,
    jobjectArray jEnvPairs,
    jstring jWorkingDir) {

    const char* exe = env->GetStringUTFChars(jExecutable, nullptr);
    const char* cwd = env->GetStringUTFChars(jWorkingDir, nullptr);
    auto args = toStringVector(env, jArgs);
    auto envPairs = toStringVector(env, jEnvPairs);

    int stdoutPipe[2]{-1, -1};
    int stderrPipe[2]{-1, -1};
    if (pipe(stdoutPipe) != 0 || pipe(stderrPipe) != 0) {
        LOGE("pipe() failed: %s", strerror(errno));
        env->ReleaseStringUTFChars(jExecutable, exe);
        env->ReleaseStringUTFChars(jWorkingDir, cwd);
        if (stdoutPipe[0] >= 0) { close(stdoutPipe[0]); close(stdoutPipe[1]); }
        if (stderrPipe[0] >= 0) { close(stderrPipe[0]); close(stderrPipe[1]); }
        return nullptr;
    }

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork() failed: %s", strerror(errno));
        close(stdoutPipe[0]); close(stdoutPipe[1]);
        close(stderrPipe[0]); close(stderrPipe[1]);
        env->ReleaseStringUTFChars(jExecutable, exe);
        env->ReleaseStringUTFChars(jWorkingDir, cwd);
        return nullptr;
    }

    if (pid == 0) {
        // Child process
        dup2(stdoutPipe[1], STDOUT_FILENO);
        dup2(stderrPipe[1], STDERR_FILENO);
        close(stdoutPipe[0]);
        close(stdoutPipe[1]);
        close(stderrPipe[0]);
        close(stderrPipe[1]);

        if (cwd && cwd[0] != '\0') {
            if (chdir(cwd) != 0) {
                _exit(126);
            }
        }

        std::vector<char*> argvPtrs;
        argvPtrs.push_back(const_cast<char*>(exe));
        for (auto& a : args) {
            argvPtrs.push_back(a.data());
        }
        argvPtrs.push_back(nullptr);

        std::vector<char*> envPtrs;
        for (auto& e : envPairs) {
            envPtrs.push_back(e.data());
        }
        envPtrs.push_back(nullptr);

        execve(exe, argvPtrs.data(), envPtrs.data());
        _exit(127);
    }

    // Parent process
    close(stdoutPipe[1]);
    close(stderrPipe[1]);
    fcntl(stdoutPipe[0], F_SETFL, O_NONBLOCK);
    fcntl(stderrPipe[0], F_SETFL, O_NONBLOCK);

    LOGI("Spawned process pid=%d executable=%s", pid, exe);
    env->ReleaseStringUTFChars(jExecutable, exe);
    env->ReleaseStringUTFChars(jWorkingDir, cwd);

    jintArray result = env->NewIntArray(3);
    jint values[3]{pid, stdoutPipe[0], stderrPipe[0]};
    env->SetIntArrayRegion(result, 0, 3, values);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeReadFdNonBlocking(
    JNIEnv* env, jobject /*thiz*/, jint fd) {
    if (fd < 0) return env->NewStringUTF("");
    char buf[4096];
    std::string collected;
    while (true) {
        ssize_t n = read(fd, buf, sizeof(buf));
        if (n > 0) {
            collected.append(buf, static_cast<size_t>(n));
            if (collected.size() >= 32768) break;
        } else {
            break;
        }
    }
    return env->NewStringUTF(collected.c_str());
}

JNIEXPORT void JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeCloseFd(
    JNIEnv* /*env*/, jobject /*thiz*/, jint fd) {
    if (fd >= 0) close(fd);
}

JNIEXPORT jint JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativePollWaitPid(
    JNIEnv* /*env*/, jobject /*thiz*/, jint pid) {
    if (pid <= 0) return -1;
    int status = 0;
    pid_t res = waitpid(pid, &status, WNOHANG);
    if (res == 0) return -999; // Still running
    if (res < 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

JNIEXPORT jboolean JNICALL
Java_com_stormrevolution_launcher_NativeBridge_nativeTerminateProcess(
    JNIEnv* /*env*/, jobject /*thiz*/, jint pid, jboolean forceKill) {
    if (pid <= 0) return JNI_FALSE;
    int sig = (forceKill == JNI_TRUE) ? SIGKILL : SIGTERM;
    int rc = kill(pid, sig);
    return rc == 0 ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"

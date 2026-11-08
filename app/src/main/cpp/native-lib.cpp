#include <jni.h>
#include "AudioEngine.h"
#include <android/log.h>
#include <string>
#include <linux/usbdevice_fs.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <string.h>

#define TAG "NativeAudioPlayer_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static AudioEngine* gEngine = nullptr;
static JavaVM* gJvm = nullptr;
static jobject gNativePlayerObject = nullptr;
static jmethodID gOnStateChangedMid = nullptr;
static jmethodID gOnUnpluggedMid = nullptr;

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    gJvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_initEngine(
    JNIEnv *env,
    jobject thiz
) {
    if (gNativePlayerObject) {
        env->DeleteGlobalRef(gNativePlayerObject);
        gNativePlayerObject = nullptr;
    }
    gNativePlayerObject = env->NewGlobalRef(thiz);

    jclass cls = env->GetObjectClass(thiz);
    gOnStateChangedMid = env->GetMethodID(cls, "onNativeStateChanged", "(ZZ)V");
    gOnUnpluggedMid = env->GetMethodID(cls, "onNativeDeviceUnplugged", "()V");

    if (!gEngine) {
        gEngine = new AudioEngine();

        gEngine->setStateChangeCallback([](bool isPlaying, bool isEnded) {
            if (gJvm && gNativePlayerObject && gOnStateChangedMid) {
                JNIEnv *env = nullptr;
                bool needsDetach = false;
                jint getEnvRes = gJvm->GetEnv((void**)&env, JNI_VERSION_1_6);
                if (getEnvRes == JNI_EDETACHED) {
                    if (gJvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                        needsDetach = true;
                    } else {
                        return;
                    }
                } else if (getEnvRes != JNI_OK || !env) {
                    return;
                }

                env->CallVoidMethod(gNativePlayerObject, gOnStateChangedMid, (jboolean)isPlaying, (jboolean)isEnded);

                if (needsDetach) {
                    gJvm->DetachCurrentThread();
                }
            }
        });

        gEngine->setUnplugCallback([]() {
            if (gJvm && gNativePlayerObject && gOnUnpluggedMid) {
                JNIEnv *env = nullptr;
                bool needsDetach = false;
                jint getEnvRes = gJvm->GetEnv((void**)&env, JNI_VERSION_1_6);
                if (getEnvRes == JNI_EDETACHED) {
                    if (gJvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                        needsDetach = true;
                    } else {
                        return;
                    }
                } else if (getEnvRes != JNI_OK || !env) {
                    return;
                }

                env->CallVoidMethod(gNativePlayerObject, gOnUnpluggedMid);

                if (needsDetach) {
                    gJvm->DetachCurrentThread();
                }
            }
        });
    }
}

JNIEXPORT jboolean JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_openFile(
    JNIEnv *env,
    jobject thiz,
    jstring file_path
) {
    if (!gEngine) return JNI_FALSE;
    const char *pathChars = env->GetStringUTFChars(file_path, nullptr);
    bool ok = gEngine->openFile(std::string(pathChars));
    env->ReleaseStringUTFChars(file_path, pathChars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_play(
    JNIEnv *env,
    jobject thiz
) {
    if (gEngine) gEngine->play();
}

JNIEXPORT void JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_pause(
    JNIEnv *env,
    jobject thiz
) {
    if (gEngine) gEngine->pause();
}

JNIEXPORT void JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_stop(
    JNIEnv *env,
    jobject thiz
) {
    if (gEngine) gEngine->stop();
}

JNIEXPORT void JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_seekTo(
    JNIEnv *env,
    jobject thiz,
    jlong position_ms
) {
    if (gEngine) gEngine->seekTo(position_ms);
}

JNIEXPORT jlong JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_getPositionMs(
    JNIEnv *env,
    jobject thiz
) {
    return gEngine ? gEngine->getCurrentPositionMs() : 0;
}

JNIEXPORT jlong JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_getDurationMs(
    JNIEnv *env,
    jobject thiz
) {
    return gEngine ? gEngine->getDurationMs() : 0;
}

JNIEXPORT jboolean JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_isPlaying(
    JNIEnv *env,
    jobject thiz
) {
    return (gEngine && gEngine->isPlaying()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_setExclusiveMode(
    JNIEnv *env,
    jobject thiz,
    jboolean exclusive
) {
    if (gEngine) gEngine->setExclusiveMode(exclusive);
}

JNIEXPORT void JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_setPreferredDeviceId(
    JNIEnv *env,
    jobject thiz,
    jint device_id
) {
    if (gEngine) gEngine->setPreferredDeviceId(device_id);
}

JNIEXPORT void JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_setVolume(
    JNIEnv *env,
    jobject thiz,
    jfloat volume
) {
    if (gEngine) gEngine->setVolume(volume);
}

JNIEXPORT jint JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_getSampleRate(
    JNIEnv *env,
    jobject thiz
) {
    return gEngine ? static_cast<jint>(gEngine->getAudioInfo().sampleRate) : 44100;
}

JNIEXPORT void JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_releaseEngine(
    JNIEnv *env,
    jobject thiz
) {
    if (gEngine) {
        delete gEngine;
        gEngine = nullptr;
    }
    if (gNativePlayerObject) {
        env->DeleteGlobalRef(gNativePlayerObject);
        gNativePlayerObject = nullptr;
    }
}

JNIEXPORT jint JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_testUsbClaim(
    JNIEnv *env,
    jobject thiz,
    jint fd,
    jint iface
) {
    LOGI(">>> testUsbClaim: fd=%d, primary iface=%d", fd, iface);

    // Disconnect kernel driver from all interfaces of the DAC
    for (int i = 0; i < 3; i++) {
        struct usbdevfs_ioctl oper;
        oper.ifno = i;
        oper.ioctl_code = USBDEVFS_DISCONNECT;
        oper.data = nullptr;

        int retDis = ioctl(fd, USBDEVFS_IOCTL, &oper);
        int errDis = errno;
        LOGI(">>> USBDEVFS_DISCONNECT iface %d result: %d (errno %d: %s)", i, retDis, errDis, strerror(errDis));
    }

    // Claim all interfaces
    for (int i = 0; i < 3; i++) {
        int ifaceNum = i;
        int retClaim = ioctl(fd, USBDEVFS_CLAIMINTERFACE, &ifaceNum);
        int errClaim = errno;
        LOGI(">>> USBDEVFS_CLAIMINTERFACE iface %d result: %d (errno %d: %s)", i, retClaim, errClaim, strerror(errClaim));
    }

    struct usbdevfs_setinterface setintf;
    setintf.interface = iface;
    setintf.altsetting = 1;
    int retSet = ioctl(fd, USBDEVFS_SETINTERFACE, &setintf);
    int errSet = errno;
    LOGI(">>> USBDEVFS_SETINTERFACE iface %d alt=1 result: %d (errno %d: %s)", iface, retSet, errSet, strerror(errSet));

    return (retSet == 0) ? 0 : -errSet;
}

JNIEXPORT jint JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_testUsbRelease(
    JNIEnv *env,
    jobject thiz,
    jint fd,
    jint iface
) {
    LOGI(">>> testUsbRelease: fd=%d, primary iface=%d", fd, iface);

    for (int i = 0; i < 3; i++) {
        int ifaceNum = i;
        int retRel = ioctl(fd, USBDEVFS_RELEASEINTERFACE, &ifaceNum);
        int errRel = errno;
        LOGI(">>> USBDEVFS_RELEASEINTERFACE iface %d result: %d (errno %d: %s)", i, retRel, errRel, strerror(errRel));

        struct usbdevfs_ioctl oper;
        oper.ifno = i;
        oper.ioctl_code = USBDEVFS_CONNECT;
        oper.data = nullptr;

        int retConn = ioctl(fd, USBDEVFS_IOCTL, &oper);
        int errConn = errno;
        LOGI(">>> USBDEVFS_CONNECT iface %d result: %d (errno %d: %s)", i, retConn, errConn, strerror(errConn));
    }

    return 0;
}

JNIEXPORT jboolean JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_setUsbStreamerDevice(
    JNIEnv *env,
    jobject thiz,
    jint fd,
    jint ac_iface,
    jint as_iface,
    jint ep_out,
    jint clock_source_id
) {
    if (!gEngine) return JNI_FALSE;
    bool ok = gEngine->setUsbDevice(fd, ac_iface, as_iface, ep_out, clock_source_id);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_saymaven_downloader_japaneseasmr_service_NativeAudioPlayer_releaseUsbStreamerDevice(
    JNIEnv *env,
    jobject thiz
) {
    if (gEngine) {
        gEngine->releaseUsbDevice();
    }
}

}



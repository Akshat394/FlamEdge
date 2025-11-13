#include <jni.h>
#include <android/log.h>

#include "processor.h"

#define LOG_TAG "EdgeNative"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_edgeviewer_app_NativeBridge_processFrame(
        JNIEnv* env,
        jobject /* clazz */,
        jobject nv21Buffer,
        jint width,
        jint height,
        jobject outBuffer) {
    if (!nv21Buffer || !outBuffer) {
        ALOGE("Buffers are null");
        return JNI_FALSE;
    }

    auto* nv21Ptr = static_cast<uint8_t*>(env->GetDirectBufferAddress(nv21Buffer));
    auto* outPtr = static_cast<uint8_t*>(env->GetDirectBufferAddress(outBuffer));
    if (!nv21Ptr || !outPtr) {
        ALOGE("Failed to get direct buffer address");
        return JNI_FALSE;
    }

    edge::FrameInfo info{width, height};
    const bool ok = edge::process_nv21_to_rgba(nv21Ptr, info, outPtr);
    return ok ? JNI_TRUE : JNI_FALSE;
}


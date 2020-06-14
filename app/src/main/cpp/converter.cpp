#include <jni.h>

typedef unsigned char Byte;

extern "C"
JNIEXPORT jobject JNICALL
Java_com_vq_1next_camera_MainActivity_YUV_1CHROMA_1DEINTERLACER(JNIEnv *env,
                                                                         jobject thiz,
                                                                         jobject plane1i,
                                                                         jobject plane2i,
                                                                         jlong len) {
    auto* outplane = new jbyte[2*len];
    auto* plane1 = static_cast<jbyte *>(env->GetDirectBufferAddress(plane1i));
    auto* plane2 = static_cast<jbyte *>(env->GetDirectBufferAddress(plane2i));

    uint64_t j = 0;
    uint64_t k = len;
    for (uint64_t i = 0; i < len-1; i += 2) {
        outplane[j] = plane1[i];
        outplane[k] = plane1[i+1];
        j++;
        k++;
    }
    outplane[j] = plane1[len-1];

    for (uint64_t i = 0; i < len-1; i += 2) {
        outplane[k] = plane2[i];
        outplane[j] = plane2[i+1];
        j++;
        k++;
    }
    outplane[k] = plane2[len-1];

    return env->NewDirectByteBuffer(outplane, 2*len);
}
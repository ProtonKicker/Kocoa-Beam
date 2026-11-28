#include <jni.h>
#include <math.h>
#include <string.h>
#include <algorithm>

extern "C" {
    JNIEXPORT jfloatArray JNICALL Java_ru_ytkab0bp_beamklipper_service_WebService_generateTone(JNIEnv *env, jclass, jint numSamples, jfloat freq) {
        if (numSamples <= 0) {
            return env->NewFloatArray(0);
        }

        jfloatArray arr = env->NewFloatArray(numSamples);
        if (arr == nullptr) {
            return nullptr;
        }
        if (freq <= 0.0f || freq >= 0.5f) {
            // Out of range: return silence
            return arr;
        }

        jfloat* sample = env->GetFloatArrayElements(arr, nullptr);
        if (sample == nullptr) {
            return arr;
        }

        // freq is cycles per sample. Period in samples = 1/freq.
        int period = (int)(1.0f / freq + 0.5f);
        if (period < 2) period = 2;

        int fill = std::min((int) numSamples, period);
        sample[0] = 0.0f;
        if (fill > 1) {
            float angle = 2.0f * (float) M_PI * freq;
            sample[1] = sinf(angle);
            float k = 2.0f * cosf(angle);
            for (int i = 2; i < fill; i++) {
                sample[i] = k * sample[i - 1] - sample[i - 2];
            }
        }

        for (int i = period; i < numSamples; i += period) {
            int copyCount = std::min((int) numSamples - i, period);
            memcpy(sample + i, sample, sizeof(float) * copyCount);
        }

        env->ReleaseFloatArrayElements(arr, sample, 0);
        return arr;
    }
}

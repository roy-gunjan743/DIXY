#include "../com_virtual_deadlock_detection_jvmti_JvmtiBridge.h"

#include <jvmti.h>
#include <iostream>

static jvmtiEnv* jvmti = nullptr;

JNIEXPORT void JNICALL
Java_com_virtual_1deadlock_1detection_jvmti_JvmtiBridge_initializeAgent
(JNIEnv* env, jobject obj) {

    JavaVM* jvm = nullptr;

    env->GetJavaVM(&jvm);

    jint result = jvm->GetEnv(
            (void**)&jvmti,
            JVMTI_VERSION_21
    );

    if (result != JNI_OK || jvmti == nullptr) {

        std::cout << "[JVMTI] Initialization Failed"
                  << std::endl;

        return;
    }

    std::cout << "[JVMTI] Initialized Successfully"
              << std::endl;

    jvmtiCapabilities capabilities = {0};

    capabilities.can_generate_monitor_events = 1;

    jvmtiError capError =
            jvmti->AddCapabilities(&capabilities);

    if (capError != JVMTI_ERROR_NONE) {

        std::cout << "[JVMTI] Failed To Add Capabilities"
                  << std::endl;

        return;
    }

    std::cout << "[JVMTI] Monitor Capabilities Enabled"
              << std::endl;
}
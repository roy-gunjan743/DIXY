#include "../com_virtual_deadlock_detection_jvmti_JvmtiBridge.h"

#include <jni.h>
#include <jvmti.h>

#include <iostream>

static jvmtiEnv* jvmti = nullptr;

/*
 * ============================================================
 * JVMTI CALLBACKS
 * ============================================================
 */

// Thread is trying to enter a locked monitor
static void JNICALL MonitorContendedEnter(
        jvmtiEnv* jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jobject object) {

    std::cout << "[JVMTI] MonitorContendedEnter" << std::endl;
}

// Thread successfully entered the monitor
static void JNICALL MonitorContendedEntered(
        jvmtiEnv* jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jobject object) {

    std::cout << "[JVMTI] MonitorContendedEntered" << std::endl;
}

// Thread starts waiting
static void JNICALL MonitorWait(
        jvmtiEnv* jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jobject object,
        jlong timeout) {

    std::cout << "[JVMTI] MonitorWait" << std::endl;
}

// Thread wakes from wait
static void JNICALL MonitorWaited(
        jvmtiEnv* jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jobject object,
        jboolean timed_out) {

    std::cout << "[JVMTI] MonitorWaited" << std::endl;
}

/*
 * ============================================================
 * INITIALIZE JVMTI
 * ============================================================
 */

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

    /*
     * ------------------------------------------------------------
     * Enable Monitor Capabilities
     * ------------------------------------------------------------
     */

    jvmtiCapabilities capabilities{};
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

    /*
     * ------------------------------------------------------------
     * Register Callbacks
     * ------------------------------------------------------------
     */

    jvmtiEventCallbacks callbacks{};

    callbacks.MonitorContendedEnter = &MonitorContendedEnter;
    callbacks.MonitorContendedEntered = &MonitorContendedEntered;
    callbacks.MonitorWait = &MonitorWait;
    callbacks.MonitorWaited = &MonitorWaited;

    jvmtiError callbackError =
            jvmti->SetEventCallbacks(
                    &callbacks,
                    sizeof(callbacks));

    if (callbackError != JVMTI_ERROR_NONE) {

        std::cout << "[JVMTI] Failed To Register Callbacks"
                  << std::endl;

        return;
    }

    std::cout << "[JVMTI] Callbacks Registered"
              << std::endl;

    /*
     * ------------------------------------------------------------
     * Enable Event Notifications
     * ------------------------------------------------------------
     */

    jvmti->SetEventNotificationMode(
            JVMTI_ENABLE,
            JVMTI_EVENT_MONITOR_CONTENDED_ENTER,
            nullptr);

    jvmti->SetEventNotificationMode(
            JVMTI_ENABLE,
            JVMTI_EVENT_MONITOR_CONTENDED_ENTERED,
            nullptr);

    jvmti->SetEventNotificationMode(
            JVMTI_ENABLE,
            JVMTI_EVENT_MONITOR_WAIT,
            nullptr);

    jvmti->SetEventNotificationMode(
            JVMTI_ENABLE,
            JVMTI_EVENT_MONITOR_WAITED,
            nullptr);

    std::cout << "[JVMTI] Monitor Events Enabled"
              << std::endl;

    std::cout << "[JVMTI] Agent Ready"
              << std::endl;
}

/*
 * ============================================================
 * SHUTDOWN
 * ============================================================
 */

JNIEXPORT void JNICALL
Java_com_virtual_1deadlock_1detection_jvmti_JvmtiBridge_shutdownAgent
(JNIEnv* env, jobject obj) {

    std::cout << "[JVMTI] Agent Shutdown"
              << std::endl;
}
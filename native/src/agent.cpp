#include "../com_virtual_deadlock_detection_jvmti_JvmtiBridge.h"

#include <jni.h>
#include <jvmti.h>

#include <iostream>
#include <mutex>
#include <atomic>
#include <chrono>

/*
 * ============================================================
 * PRODUCTION LOGGING CONTROL
 * ============================================================
 */
// Comment out the next line to build for production (disables all JVMTI stdout)
#define VDD_DEBUG_MODE 1

#ifdef VDD_DEBUG_MODE
    #define AGENT_LOG(msg) std::cout << "[JVMTI] " << msg << std::endl
#else
    #define AGENT_LOG(msg) // Compiles to nothing in production
#endif

/*
 * ============================================================
 * CONFIG — matches the real NativeEventBridge.java
 * ============================================================
 */
static const char* TARGET_CLASS =
    "com/virtual_deadlock_detection/LockDetectionAgent/jvmti/NativeEventBridge";
static const char* TARGET_METHOD = "publishLockOwned";
// (long waitingThreadId, String waitingThreadName, long ownerThreadId,
//  String ownerThreadName, long lockAddress, long timestampMillis)
static const char* TARGET_METHOD_SIG = "(JLjava/lang/String;JLjava/lang/String;JJ)V";

// Fired when a previously-blocked thread actually acquires the monitor.
// Tells Java to remove the wait-for edge for (threadId, lockAddress) —
// this thread is no longer waiting on anything.
static const char* TARGET_METHOD_RELEASED = "publishLockReleased";
// (long threadId, long lockAddress, long timestampMillis)
static const char* TARGET_METHOD_RELEASED_SIG = "(JJJ)V";

/*
 * ============================================================
 * GLOBAL STATE
 * ============================================================
 */
static JavaVM*    g_jvm = nullptr;
static jvmtiEnv*  jvmti = nullptr;

static jclass     g_bridgeClass           = nullptr; // global ref
static jmethodID  g_publishLockOwnedId    = nullptr;
static jmethodID  g_publishLockReleasedId = nullptr;

// Separate stable-ID counters: one namespace for threads, one for monitors
// (lock addresses). JVMTI tags are per-object, so there is no collision
// risk between the two even though both counters start at 1.
static std::atomic<jlong> g_nextThreadId{1};
static std::atomic<jlong> g_nextMonitorId{1};

// Guards the check-then-set race when two threads contend the same
// monitor (or reference the same thread object) at the same instant.
static std::mutex g_tagMutex;

/*
 * ============================================================
 * HELPERS
 * ============================================================
 */

// Returns the existing stable tag for `obj`, or assigns a new one from
// `counter` if this is the first time we've seen it. Thread-safe.
static jlong getOrAssignTag(jvmtiEnv* env, jobject obj, std::atomic<jlong>& counter) {
    std::lock_guard<std::mutex> lock(g_tagMutex);

    jlong tag = 0;
    jvmtiError err = env->GetTag(obj, &tag);
    if (err != JVMTI_ERROR_NONE) {
        AGENT_LOG("GetTag failed, err=" << err);
        return -1;
    }

    if (tag == 0) {
        tag = counter.fetch_add(1);
        err = env->SetTag(obj, tag);
        if (err != JVMTI_ERROR_NONE) {
            AGENT_LOG("SetTag failed, err=" << err);
            return -1;
        }
    }

    return tag;
}

// Fetches a thread's real name via GetThreadInfo, releasing all
// JVMTI-allocated resources and JNI local references it returns.
static bool getThreadName(jvmtiEnv* jvmti_env, JNIEnv* jni_env, jthread thread, std::string& outName) {
    jvmtiThreadInfo info{};
    jvmtiError err = jvmti_env->GetThreadInfo(thread, &info);
    if (err != JVMTI_ERROR_NONE) {
        outName = "<unknown>";
        return false;
    }

    outName = (info.name != nullptr) ? info.name : "<unnamed>";

    // Clean up JVMTI allocated string
    if (info.name != nullptr) {
        jvmti_env->Deallocate((unsigned char*)info.name);
    }

    // CRITICAL FIX: Clean up JNI local references to prevent JNI table overflow
    if (info.thread_group != nullptr) {
        jni_env->DeleteLocalRef(info.thread_group);
    }
    if (info.context_class_loader != nullptr) {
        jni_env->DeleteLocalRef(info.context_class_loader);
    }

    return true;
}

static jlong currentTimestampMillis() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()
    ).count();
}

/*
 * ============================================================
 * JVMTI CALLBACKS
 * ============================================================
 */

// Thread is trying to enter a locked monitor.
// This is the ONLY place we collect data — no cycle detection, no
// dependency-chain walking. We report the raw fact: "waitingThread is
// blocked trying to enter a monitor currently held by ownerThread."
// All graph construction and deadlock detection happen in Java.
static void JNICALL MonitorContendedEnter(
        jvmtiEnv* jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jobject object) {

    // 1. Get the real current owner of the monitor.
    jvmtiMonitorUsage usage{};
    jvmtiError err = jvmti_env->GetObjectMonitorUsage(object, &usage);
    if (err != JVMTI_ERROR_NONE) {
        AGENT_LOG("GetObjectMonitorUsage failed, err=" << err);
        return;
    }

    jthread owner = usage.owner;

    if (usage.waiters != nullptr) {
        jvmti_env->Deallocate((unsigned char*)usage.waiters);
    }
    if (usage.notify_waiters != nullptr) {
        jvmti_env->Deallocate((unsigned char*)usage.notify_waiters);
    }

    if (owner == nullptr) {
        // Lock was released between the event firing and us inspecting
        // it — nothing meaningful to report for this instant.
        return;
    }

    // 2. Assign/retrieve stable IDs using real JVMTI tags (no fake IDs).
    jlong waitingThreadId = getOrAssignTag(jvmti_env, thread, g_nextThreadId);
    jlong ownerThreadId   = getOrAssignTag(jvmti_env, owner, g_nextThreadId);
    jlong lockAddress     = getOrAssignTag(jvmti_env, object, g_nextMonitorId);

    if (waitingThreadId < 0 || ownerThreadId < 0 || lockAddress < 0) {
        AGENT_LOG("Failed to assign tags, dropping event");
        return;
    }

    // 3. Get real thread names.
    std::string waitingThreadName;
    std::string ownerThreadName;
    getThreadName(jvmti_env, jni_env, thread, waitingThreadName);
    getThreadName(jvmti_env, jni_env, owner, ownerThreadName);

    jlong timestamp = currentTimestampMillis();

    AGENT_LOG("LockOwned event: waiting="
              << waitingThreadName << "(" << waitingThreadId << ") owner="
              << ownerThreadName << "(" << ownerThreadId << ") lock="
              << lockAddress);

    // 4. Forward straight to Java via publishLockOwned(...). No local
    //    decision-making about deadlocks happens here.
    if (g_bridgeClass == nullptr || g_publishLockOwnedId == nullptr) {
        AGENT_LOG("Bridge class/method not initialized, dropping event");
        return;
    }

    jstring jWaitingName = jni_env->NewStringUTF(waitingThreadName.c_str());
    jstring jOwnerName   = jni_env->NewStringUTF(ownerThreadName.c_str());

    jni_env->CallStaticVoidMethod(
        g_bridgeClass,
        g_publishLockOwnedId,
        waitingThreadId,
        jWaitingName,
        ownerThreadId,
        jOwnerName,
        lockAddress,
        timestamp
    );

    if (jni_env->ExceptionCheck()) {
        AGENT_LOG("Exception thrown while calling publishLockOwned");
        jni_env->ExceptionDescribe();
        jni_env->ExceptionClear();
    }

    jni_env->DeleteLocalRef(jWaitingName);
    jni_env->DeleteLocalRef(jOwnerName);
}

// Thread successfully entered the monitor — it is no longer waiting on
// anything. We report this so Java can remove the corresponding wait-for
// edge from the graph. No decision-making happens here; Java owns the
// graph and decides what removing this edge means for any in-progress
// cycle detection.
static void JNICALL MonitorContendedEntered(
        jvmtiEnv* jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jobject object) {

    AGENT_LOG("MonitorContendedEntered");

    if (g_bridgeClass == nullptr || g_publishLockReleasedId == nullptr) {
        AGENT_LOG("Bridge class/method not initialized, dropping event");
        return;
    }

    // These objects were already tagged when the contention started;
    // getOrAssignTag simply fetches the existing tag in the normal case,
    // and only assigns a fresh one as a defensive fallback (e.g. if this
    // event fires for a monitor/thread we somehow never saw contended).
    jlong threadId    = getOrAssignTag(jvmti_env, thread, g_nextThreadId);
    jlong lockAddress = getOrAssignTag(jvmti_env, object, g_nextMonitorId);

    if (threadId < 0 || lockAddress < 0) {
        AGENT_LOG("Failed to resolve tags, dropping event");
        return;
    }

    jlong timestamp = currentTimestampMillis();

    AGENT_LOG("LockReleased event (wait edge removed): thread="
              << threadId << " lock=" << lockAddress);

    jni_env->CallStaticVoidMethod(
        g_bridgeClass,
        g_publishLockReleasedId,
        threadId,
        lockAddress,
        timestamp
    );

    if (jni_env->ExceptionCheck()) {
        AGENT_LOG("Exception thrown while calling publishLockReleased");
        jni_env->ExceptionDescribe();
        jni_env->ExceptionClear();
    }
}

// Thread starts waiting on Object.wait()
static void JNICALL MonitorWait(
        jvmtiEnv* jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jobject object,
        jlong timeout) {

    AGENT_LOG("MonitorWait");
}

// Thread wakes from Object.wait()
static void JNICALL MonitorWaited(
        jvmtiEnv* jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jobject object,
        jboolean timed_out) {

    AGENT_LOG("MonitorWaited");
}

/*
 * ============================================================
 * INITIALIZE JVMTI
 * ============================================================
 */

JNIEXPORT void JNICALL
Java_com_virtual_1deadlock_1detection_LockDetectionAgent_jvmti_JvmtiBridge_initializeAgent
(JNIEnv* env, jobject obj) {

    JavaVM* jvm = nullptr;
    env->GetJavaVM(&jvm);
    g_jvm = jvm;

    jint result = jvm->GetEnv((void**)&jvmti, JVMTI_VERSION_21);

    if (result != JNI_OK || jvmti == nullptr) {
        AGENT_LOG("Initialization Failed");
        return;
    }

    AGENT_LOG("Initialized Successfully");

    /*
     * ------------------------------------------------------------
     * Enable Capabilities
     * ------------------------------------------------------------
     * can_generate_monitor_events -> contended enter/entered/wait/waited
     * can_tag_objects             -> stable IDs via SetTag/GetTag
     * can_get_monitor_info        -> GetObjectMonitorUsage (real owner thread)
     * ------------------------------------------------------------
     */

    jvmtiCapabilities capabilities{};
    capabilities.can_generate_monitor_events = 1;
    capabilities.can_tag_objects = 1;
    capabilities.can_get_monitor_info = 1;

    jvmtiError capError = jvmti->AddCapabilities(&capabilities);
    if (capError != JVMTI_ERROR_NONE) {
        AGENT_LOG("Failed To Add Capabilities, err=" << capError);
        return;
    }

    AGENT_LOG("Monitor Capabilities Enabled");

    /*
     * ------------------------------------------------------------
     * Resolve the Java bridge class + method ONCE, up front, on the
     * thread/classloader that called initializeAgent(). Caching a
     * global ref avoids relying on FindClass working correctly from
     * arbitrary JVMTI callback threads later, which can have
     * different classloader context.
     * ------------------------------------------------------------
     */

    jclass localClass = env->FindClass(TARGET_CLASS);
    if (localClass == nullptr) {
        AGENT_LOG("Failed to find class: " << TARGET_CLASS);
        if (env->ExceptionCheck()) env->ExceptionClear();
        return;
    }

    g_bridgeClass = (jclass)env->NewGlobalRef(localClass);
    env->DeleteLocalRef(localClass);

    g_publishLockOwnedId = env->GetStaticMethodID(g_bridgeClass, TARGET_METHOD, TARGET_METHOD_SIG);
    if (g_publishLockOwnedId == nullptr) {
        AGENT_LOG("Failed to find method: " << TARGET_METHOD
                  << " with signature " << TARGET_METHOD_SIG);
        if (env->ExceptionCheck()) env->ExceptionClear();
        return;
    }

    g_publishLockReleasedId = env->GetStaticMethodID(
        g_bridgeClass, TARGET_METHOD_RELEASED, TARGET_METHOD_RELEASED_SIG);
    if (g_publishLockReleasedId == nullptr) {
        AGENT_LOG("Failed to find method: " << TARGET_METHOD_RELEASED
                  << " with signature " << TARGET_METHOD_RELEASED_SIG);
        if (env->ExceptionCheck()) env->ExceptionClear();
        return;
    }

    AGENT_LOG("Bridge class and method resolved");

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

    jvmtiError callbackError = jvmti->SetEventCallbacks(&callbacks, sizeof(callbacks));
    if (callbackError != JVMTI_ERROR_NONE) {
        AGENT_LOG("Failed To Register Callbacks, err=" << callbackError);
        return;
    }

    AGENT_LOG("Callbacks Registered");

    /*
     * ------------------------------------------------------------
     * Enable Event Notifications
     * ------------------------------------------------------------
     */

    jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_MONITOR_CONTENDED_ENTER, nullptr);
    jvmti->SetEventNotificationMode(JVMTI_ENABLE, JVMTI_EVENT_MONITOR_CONTENDED_ENTERED, nullptr);
    // MONITOR_WAIT / MONITOR_WAITED intentionally left disabled: they never
    // call into Java (see MonitorWait/MonitorWaited above — they only
    // std::cout), and they fire constantly on internal JVM/JFR threads,
    // which was flooding the interactive shell's stdout and made the
    // "vdd:>" prompt unusable. Re-enable only if you actually need to
    // observe raw wait/notify traffic.

    AGENT_LOG("Monitor Events Enabled");
    AGENT_LOG("Agent Ready — pure event collector mode (no native deadlock detection)");
}

/*
 * ============================================================
 * SHUTDOWN
 * ============================================================
 */

JNIEXPORT void JNICALL
Java_com_virtual_1deadlock_1detection_LockDetectionAgent_jvmti_JvmtiBridge_shutdownAgent
(JNIEnv* env, jobject obj) {

    if (g_bridgeClass != nullptr) {
        env->DeleteGlobalRef(g_bridgeClass);
        g_bridgeClass = nullptr;
    }

    AGENT_LOG("Agent Shutdown");
}
#include "../com_virtual_deadlock_detection_jvmti_JvmtiBridge.h"

#include <jni.h>
#include <jvmti.h>

#include <iostream>
#include <mutex>
#include <atomic>
#include <chrono>
#include <string>

/*
 * ============================================================
 * PRODUCTION LOGGING CONTROL
 * ============================================================
 *
 * Keep enabled while debugging.
 *
 * For production:
 *
 *     // #define VDD_DEBUG_MODE 1
 *
 */
#define VDD_DEBUG_MODE 1

#ifdef VDD_DEBUG_MODE

#define AGENT_LOG(msg) \
    std::cout << "[JVMTI] " << msg << std::endl

#else

#define AGENT_LOG(msg)

#endif


/*
 * ============================================================
 * CONFIG
 * ============================================================
 *
 * These values must match NativeEventBridge.java.
 *
 */

static const char* TARGET_CLASS =
    "com/virtual_deadlock_detection/LockDetectionAgent/jvmti/NativeEventBridge";

static const char* TARGET_METHOD =
    "publishLockOwned";

/*
 * Java signature:
 *
 * long waitingThreadId
 * String waitingThreadName
 * long ownerThreadId
 * String ownerThreadName
 * long lockId
 * long timestampMillis
 */
static const char* TARGET_METHOD_SIG =
    "(JLjava/lang/String;JLjava/lang/String;JJ)V";


/*
 * Fired when a previously blocked thread successfully
 * acquires the monitor.
 *
 * Java removes the corresponding wait edge.
 */
static const char* TARGET_METHOD_RELEASED =
    "publishLockReleased";

/*
 * Java signature:
 *
 * long threadId
 * long lockId
 * long timestampMillis
 */
static const char* TARGET_METHOD_RELEASED_SIG =
    "(JJJ)V";


/*
 * ============================================================
 * GLOBAL STATE
 * ============================================================
 */

static JavaVM* g_jvm = nullptr;

static jvmtiEnv* jvmti = nullptr;

static jclass g_bridgeClass = nullptr;

static jmethodID g_publishLockOwnedId = nullptr;

static jmethodID g_publishLockReleasedId = nullptr;


/*
 * Separate namespaces:
 *
 * thread IDs
 * monitor IDs
 *
 * JVMTI tags are attached to Java objects.
 */
static std::atomic<jlong> g_nextThreadId{1};

static std::atomic<jlong> g_nextMonitorId{1};


/*
 * Protects GetTag -> SetTag race.
 */
static std::mutex g_tagMutex;


/*
 * ============================================================
 * INTERNAL THREAD FILTER
 * ============================================================
 *
 * The VDD agent itself creates/uses several threads.
 *
 * We MUST NOT feed their monitor activity back into the
 * deadlock graph.
 *
 * Otherwise we get:
 *
 *     VDD
 *      ↓
 *     HTTP client
 *      ↓
 *     monitor contention
 *      ↓
 *     JVMTI
 *      ↓
 *     VDD
 *      ↓
 *     HTTP client
 *      ↓
 *     ...
 *
 * These are infrastructure events, not application deadlocks.
 *
 */

static bool isInternalAgentThread(const std::string& name) {

    if (name.empty()) {
        return true;
    }


    /*
     * JVM / Spring startup infrastructure.
     */
    if (name == "background-preinit") {
        return true;
    }


    /*
     * VDD internal state polling thread.
     */
    if (name.rfind("vld-thread-state-poller", 0) == 0) {
        return true;
    }


    /*
     * Java HttpClient threads used by the VDD reporting
     * pipeline.
     */
    if (name.rfind("HttpClient-", 0) == 0) {
        return true;
    }


    /*
     * JFR internal event streaming thread.
     */
    if (name.rfind("JFR Event Stream", 0) == 0) {
        return true;
    }


    /*
     * VDD internal worker naming variants.
     */
    if (name.rfind("VDD-", 0) == 0) {
        return true;
    }

    if (name.rfind("vdd-", 0) == 0) {
        return true;
    }


    return false;
}


/*
 * ============================================================
 * HELPERS
 * ============================================================
 */


/*
 * Returns the existing stable JVMTI tag.
 *
 * If the object does not have a tag yet, assign one.
 *
 * The tag is attached to the actual Java object, so the same
 * object receives the same ID for its lifetime.
 */
static jlong getOrAssignTag(
        jvmtiEnv* env,
        jobject obj,
        std::atomic<jlong>& counter) {

    if (env == nullptr || obj == nullptr) {
        return -1;
    }

    std::lock_guard<std::mutex> lock(g_tagMutex);

    jlong tag = 0;

    jvmtiError err =
        env->GetTag(obj, &tag);

    if (err != JVMTI_ERROR_NONE) {

        AGENT_LOG(
            "GetTag failed, err="
            << err
        );

        return -1;
    }


    if (tag == 0) {

        tag = counter.fetch_add(1);

        err =
            env->SetTag(
                obj,
                tag
            );

        if (err != JVMTI_ERROR_NONE) {

            AGENT_LOG(
                "SetTag failed, err="
                << err
            );

            return -1;
        }
    }


    return tag;
}


/*
 * ============================================================
 * GET REAL JAVA THREAD NAME
 * ============================================================
 */

static bool getThreadName(
        jvmtiEnv* jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        std::string& outName) {

    if (jvmti_env == nullptr ||
        jni_env == nullptr ||
        thread == nullptr) {

        outName = "<unknown>";

        return false;
    }


    jvmtiThreadInfo info{};

    jvmtiError err =
        jvmti_env->GetThreadInfo(
            thread,
            &info
        );

    if (err != JVMTI_ERROR_NONE) {

        outName = "<unknown>";

        return false;
    }


    outName =
        (info.name != nullptr)
            ? info.name
            : "<unnamed>";


    /*
     * JVMTI allocated memory.
     */
    if (info.name != nullptr) {

        jvmti_env->Deallocate(
            reinterpret_cast<unsigned char*>(info.name)
        );
    }


    /*
     * JNI local references.
     */
    if (info.thread_group != nullptr) {

        jni_env->DeleteLocalRef(
            info.thread_group
        );
    }


    if (info.context_class_loader != nullptr) {

        jni_env->DeleteLocalRef(
            info.context_class_loader
        );
    }


    return true;
}


/*
 * ============================================================
 * TIMESTAMP
 * ============================================================
 */

static jlong currentTimestampMillis() {

    return std::chrono::duration_cast<
        std::chrono::milliseconds
    >(
        std::chrono::system_clock::now()
        .time_since_epoch()
    ).count();
}


/*
 * ============================================================
 * JVMTI CALLBACK
 *
 * MONITOR CONTENDED ENTER
 * ============================================================
 *
 * A thread is trying to enter a monitor currently held
 * by another thread.
 *
 * We only collect the raw relationship:
 *
 *     waitingThread
 *           ↓
 *        waits for
 *           ↓
 *          lock
 *           ↓
 *         held by
 *           ↓
 *       ownerThread
 *
 * Deadlock detection is performed by Java.
 *
 */

static void JNICALL MonitorContendedEnter(
        jvmtiEnv* jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jobject object) {


    /*
     * Basic validation.
     */
    if (jvmti_env == nullptr ||
        jni_env == nullptr ||
        thread == nullptr ||
        object == nullptr) {

        return;
    }


    /*
     * ========================================================
     * 1. GET WAITING THREAD NAME FIRST
     * ========================================================
     *
     * This is intentionally done BEFORE:
     *
     *     GetObjectMonitorUsage
     *     GetTag
     *     SetTag
     *     logging
     *     Java callback
     *
     * This allows us to drop VDD/JVM infrastructure events
     * immediately.
     */

    std::string waitingThreadName;

    if (!getThreadName(
            jvmti_env,
            jni_env,
            thread,
            waitingThreadName)) {

        return;
    }


    /*
     * Ignore VDD/JVM infrastructure.
     */
    if (isInternalAgentThread(
            waitingThreadName)) {

        return;
    }


    /*
     * ========================================================
     * 2. GET MONITOR OWNER
     * ========================================================
     */

    jvmtiMonitorUsage usage{};

    jvmtiError err =
        jvmti_env->GetObjectMonitorUsage(
            object,
            &usage
        );

    if (err != JVMTI_ERROR_NONE) {

        return;
    }


    jthread owner =
        usage.owner;


    /*
     * Monitor no longer has an owner.
     */
    if (owner == nullptr) {

        if (usage.waiters != nullptr) {

            jvmti_env->Deallocate(
                reinterpret_cast<unsigned char*>(
                    usage.waiters
                )
            );
        }


        if (usage.notify_waiters != nullptr) {

            jvmti_env->Deallocate(
                reinterpret_cast<unsigned char*>(
                    usage.notify_waiters
                )
            );
        }


        return;
    }


    /*
     * ========================================================
     * 3. GET OWNER THREAD NAME
     * ========================================================
     */

    std::string ownerThreadName;

    if (!getThreadName(
            jvmti_env,
            jni_env,
            owner,
            ownerThreadName)) {

        if (usage.waiters != nullptr) {

            jvmti_env->Deallocate(
                reinterpret_cast<unsigned char*>(
                    usage.waiters
                )
            );
        }


        if (usage.notify_waiters != nullptr) {

            jvmti_env->Deallocate(
                reinterpret_cast<unsigned char*>(
                    usage.notify_waiters
                )
            );
        }


        return;
    }


    /*
     * Ignore events where the owner itself is an internal
     * VDD/JVM infrastructure thread.
     */
    if (isInternalAgentThread(
            ownerThreadName)) {

        if (usage.waiters != nullptr) {

            jvmti_env->Deallocate(
                reinterpret_cast<unsigned char*>(
                    usage.waiters
                )
            );
        }


        if (usage.notify_waiters != nullptr) {

            jvmti_env->Deallocate(
                reinterpret_cast<unsigned char*>(
                    usage.notify_waiters
                )
            );
        }


        return;
    }


    /*
     * ========================================================
     * 4. ASSIGN STABLE IDs
     * ========================================================
     */

    jlong waitingThreadId =
        getOrAssignTag(
            jvmti_env,
            thread,
            g_nextThreadId
        );


    jlong ownerThreadId =
        getOrAssignTag(
            jvmti_env,
            owner,
            g_nextThreadId
        );


    /*
     * This is technically a JVMTI tag, not a raw memory
     * address.
     */
    jlong lockId =
        getOrAssignTag(
            jvmti_env,
            object,
            g_nextMonitorId
        );


    if (waitingThreadId < 0 ||
        ownerThreadId < 0 ||
        lockId < 0) {

        AGENT_LOG(
            "Failed to assign tags, dropping event"
        );


        if (usage.waiters != nullptr) {

            jvmti_env->Deallocate(
                reinterpret_cast<unsigned char*>(
                    usage.waiters
                )
            );
        }


        if (usage.notify_waiters != nullptr) {

            jvmti_env->Deallocate(
                reinterpret_cast<unsigned char*>(
                    usage.notify_waiters
                )
            );
        }


        return;
    }


    /*
     * ========================================================
     * 5. TIMESTAMP
     * ========================================================
     */

    jlong timestamp =
        currentTimestampMillis();


    /*
     * ========================================================
     * 6. LOG REAL APPLICATION EVENT
     * ========================================================
     */

    AGENT_LOG(
        "LockOwned event: waiting="
        << waitingThreadName
        << "("
        << waitingThreadId
        << ") owner="
        << ownerThreadName
        << "("
        << ownerThreadId
        << ") lock="
        << lockId
    );


    /*
     * ========================================================
     * 7. JAVA BRIDGE CHECK
     * ========================================================
     */

    if (g_bridgeClass == nullptr ||
        g_publishLockOwnedId == nullptr) {

        AGENT_LOG(
            "Bridge class/method not initialized, "
            "dropping event"
        );


        if (usage.waiters != nullptr) {

            jvmti_env->Deallocate(
                reinterpret_cast<unsigned char*>(
                    usage.waiters
                )
            );
        }


        if (usage.notify_waiters != nullptr) {

            jvmti_env->Deallocate(
                reinterpret_cast<unsigned char*>(
                    usage.notify_waiters
                )
            );
        }


        return;
    }


    /*
     * ========================================================
     * 8. CREATE JAVA STRINGS
     * ========================================================
     */

    jstring jWaitingName =
        jni_env->NewStringUTF(
            waitingThreadName.c_str()
        );


    jstring jOwnerName =
        jni_env->NewStringUTF(
            ownerThreadName.c_str()
        );


    if (jWaitingName == nullptr ||
        jOwnerName == nullptr) {

        if (jWaitingName != nullptr) {

            jni_env->DeleteLocalRef(
                jWaitingName
            );
        }


        if (jOwnerName != nullptr) {

            jni_env->DeleteLocalRef(
                jOwnerName
            );
        }


        if (usage.waiters != nullptr) {

            jvmti_env->Deallocate(
                reinterpret_cast<unsigned char*>(
                    usage.waiters
                )
            );
        }


        if (usage.notify_waiters != nullptr) {

            jvmti_env->Deallocate(
                reinterpret_cast<unsigned char*>(
                    usage.notify_waiters
                )
            );
        }


        return;
    }


    /*
     * ========================================================
     * 9. FORWARD TO JAVA
     * ========================================================
     */

    jni_env->CallStaticVoidMethod(
        g_bridgeClass,
        g_publishLockOwnedId,

        waitingThreadId,
        jWaitingName,

        ownerThreadId,
        jOwnerName,

        lockId,
        timestamp
    );


    /*
     * ========================================================
     * 10. HANDLE JAVA EXCEPTION
     * ========================================================
     */

    if (jni_env->ExceptionCheck()) {

        AGENT_LOG(
            "Exception thrown while calling "
            "publishLockOwned"
        );

        jni_env->ExceptionDescribe();

        jni_env->ExceptionClear();
    }


    /*
     * ========================================================
     * 11. CLEAN JNI REFERENCES
     * ========================================================
     */

    jni_env->DeleteLocalRef(
        jWaitingName
    );

    jni_env->DeleteLocalRef(
        jOwnerName
    );


    /*
     * ========================================================
     * 12. CLEAN JVMTI MONITOR USAGE MEMORY
     * ========================================================
     */

    if (usage.waiters != nullptr) {

        jvmti_env->Deallocate(
            reinterpret_cast<unsigned char*>(
                usage.waiters
            )
        );
    }


    if (usage.notify_waiters != nullptr) {

        jvmti_env->Deallocate(
            reinterpret_cast<unsigned char*>(
                usage.notify_waiters
            )
        );
    }
}


/*
 * ============================================================
 * JVMTI CALLBACK
 *
 * MONITOR CONTENDED ENTERED
 * ============================================================
 *
 * The thread successfully entered the monitor.
 *
 * Therefore Java should remove:
 *
 *     thread → lock
 *
 * WAIT_FOR edge.
 *
 */

static void JNICALL MonitorContendedEntered(
        jvmtiEnv* jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jobject object) {


    if (jvmti_env == nullptr ||
        jni_env == nullptr ||
        thread == nullptr ||
        object == nullptr) {

        return;
    }


    /*
     * Get thread name first so internal infrastructure
     * events can be ignored immediately.
     */

    std::string threadName;

    if (!getThreadName(
            jvmti_env,
            jni_env,
            thread,
            threadName)) {

        return;
    }


    /*
     * Ignore VDD/JVM infrastructure.
     */
    if (isInternalAgentThread(
            threadName)) {

        return;
    }


    /*
     * Java bridge must exist.
     */
    if (g_bridgeClass == nullptr ||
        g_publishLockReleasedId == nullptr) {

        return;
    }


    /*
     * ========================================================
     * STABLE IDS
     * ========================================================
     */

    jlong threadId =
        getOrAssignTag(
            jvmti_env,
            thread,
            g_nextThreadId
        );


    jlong lockId =
        getOrAssignTag(
            jvmti_env,
            object,
            g_nextMonitorId
        );


    if (threadId < 0 ||
        lockId < 0) {

        return;
    }


    /*
     * Timestamp.
     */
    jlong timestamp =
        currentTimestampMillis();


    /*
     * Log only real application events.
     */
    AGENT_LOG(
        "LockReleased event: thread="
        << threadName
        << "("
        << threadId
        << ") lock="
        << lockId
    );


    /*
     * ========================================================
     * JAVA CALLBACK
     * ========================================================
     */

    jni_env->CallStaticVoidMethod(
        g_bridgeClass,
        g_publishLockReleasedId,

        threadId,
        lockId,
        timestamp
    );


    /*
     * Handle Java exception without allowing it to remain
     * pending on the callback thread.
     */
    if (jni_env->ExceptionCheck()) {

        AGENT_LOG(
            "Exception thrown while calling "
            "publishLockReleased"
        );

        jni_env->ExceptionDescribe();

        jni_env->ExceptionClear();
    }
}


/*
 * ============================================================
 * MONITOR WAIT
 * ============================================================
 *
 * Disabled at registration below.
 *
 */

static void JNICALL MonitorWait(
        jvmtiEnv* jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jobject object,
        jlong timeout) {

    /*
     * Intentionally no processing.
     *
     * These events are extremely frequent and are not required
     * by the current wait-for graph implementation.
     */
}


/*
 * ============================================================
 * MONITOR WAITED
 * ============================================================
 *
 * Disabled at registration below.
 *
 */

static void JNICALL MonitorWaited(
        jvmtiEnv* jvmti_env,
        JNIEnv* jni_env,
        jthread thread,
        jobject object,
        jboolean timed_out) {

    /*
     * Intentionally no processing.
     */
}


/*
 * ============================================================
 * INITIALIZE JVMTI
 * ============================================================
 */

JNIEXPORT void JNICALL
Java_com_virtual_1deadlock_1detection_LockDetectionAgent_jvmti_JvmtiBridge_initializeAgent
(
        JNIEnv* env,
        jobject obj) {


    /*
     * ========================================================
     * GET JVM
     * ========================================================
     */

    JavaVM* jvm = nullptr;

    env->GetJavaVM(
        &jvm
    );

    g_jvm = jvm;


    /*
     * ========================================================
     * GET JVMTI ENVIRONMENT
     * ========================================================
     */

    jint result =
        jvm->GetEnv(
            reinterpret_cast<void**>(&jvmti),
            JVMTI_VERSION_21
        );


    if (result != JNI_OK ||
        jvmti == nullptr) {

        AGENT_LOG(
            "Initialization Failed"
        );

        return;
    }


    AGENT_LOG(
        "Initialized Successfully"
    );


    /*
     * ========================================================
     * ENABLE JVMTI CAPABILITIES
     * ========================================================
     *
     * can_generate_monitor_events
     * can_tag_objects
     * can_get_monitor_info
     *
     */

    jvmtiCapabilities capabilities{};

    capabilities.can_generate_monitor_events = 1;

    capabilities.can_tag_objects = 1;

    capabilities.can_get_monitor_info = 1;


    jvmtiError capError =
        jvmti->AddCapabilities(
            &capabilities
        );


    if (capError != JVMTI_ERROR_NONE) {

        AGENT_LOG(
            "Failed To Add Capabilities, err="
            << capError
        );

        return;
    }


    AGENT_LOG(
        "Monitor Capabilities Enabled"
    );


    /*
     * ========================================================
     * RESOLVE JAVA BRIDGE CLASS
     * ========================================================
     *
     * We resolve the class once using the class loader of the
     * thread which initializes the bridge.
     *
     * Then keep a global reference.
     *
     */

    jclass localClass =
        env->FindClass(
            TARGET_CLASS
        );


    if (localClass == nullptr) {

        AGENT_LOG(
            "Failed to find class: "
            << TARGET_CLASS
        );

        if (env->ExceptionCheck()) {

            env->ExceptionClear();
        }

        return;
    }


    /*
     * Global reference survives callback execution.
     */

    g_bridgeClass =
        reinterpret_cast<jclass>(
            env->NewGlobalRef(
                localClass
            )
        );


    env->DeleteLocalRef(
        localClass
    );


    if (g_bridgeClass == nullptr) {

        AGENT_LOG(
            "Failed to create global reference "
            "for NativeEventBridge"
        );

        return;
    }


    /*
     * ========================================================
     * RESOLVE publishLockOwned
     * ========================================================
     */

    g_publishLockOwnedId =
        env->GetStaticMethodID(
            g_bridgeClass,
            TARGET_METHOD,
            TARGET_METHOD_SIG
        );


    if (g_publishLockOwnedId == nullptr) {

        AGENT_LOG(
            "Failed to find method: "
            << TARGET_METHOD
            << " with signature "
            << TARGET_METHOD_SIG
        );


        if (env->ExceptionCheck()) {

            env->ExceptionClear();
        }


        env->DeleteGlobalRef(
            g_bridgeClass
        );

        g_bridgeClass = nullptr;

        return;
    }


    /*
     * ========================================================
     * RESOLVE publishLockReleased
     * ========================================================
     */

    g_publishLockReleasedId =
        env->GetStaticMethodID(
            g_bridgeClass,
            TARGET_METHOD_RELEASED,
            TARGET_METHOD_RELEASED_SIG
        );


    if (g_publishLockReleasedId == nullptr) {

        AGENT_LOG(
            "Failed to find method: "
            << TARGET_METHOD_RELEASED
            << " with signature "
            << TARGET_METHOD_RELEASED_SIG
        );


        if (env->ExceptionCheck()) {

            env->ExceptionClear();
        }


        env->DeleteGlobalRef(
            g_bridgeClass
        );

        g_bridgeClass = nullptr;

        g_publishLockOwnedId = nullptr;

        return;
    }


    AGENT_LOG(
        "Bridge class and method resolved"
    );


    /*
     * ========================================================
     * REGISTER CALLBACKS
     * ========================================================
     */

    jvmtiEventCallbacks callbacks{};


    callbacks.MonitorContendedEnter =
        &MonitorContendedEnter;


    callbacks.MonitorContendedEntered =
        &MonitorContendedEntered;


    callbacks.MonitorWait =
        &MonitorWait;


    callbacks.MonitorWaited =
        &MonitorWaited;


    jvmtiError callbackError =
        jvmti->SetEventCallbacks(
            &callbacks,
            sizeof(callbacks)
        );


    if (callbackError != JVMTI_ERROR_NONE) {

        AGENT_LOG(
            "Failed To Register Callbacks, err="
            << callbackError
        );

        return;
    }


    AGENT_LOG(
        "Callbacks Registered"
    );


    /*
     * ========================================================
     * ENABLE EVENT NOTIFICATIONS
     * ========================================================
     *
     * We need:
     *
     *     MONITOR_CONTENDED_ENTER
     *     MONITOR_CONTENDED_ENTERED
     *
     * We intentionally keep:
     *
     *     MONITOR_WAIT
     *     MONITOR_WAITED
     *
     * disabled because they are extremely noisy and are not
     * required by the current WaitForGraph implementation.
     */

    jvmtiError e1 =
        jvmti->SetEventNotificationMode(
            JVMTI_ENABLE,
            JVMTI_EVENT_MONITOR_CONTENDED_ENTER,
            nullptr
        );


    if (e1 != JVMTI_ERROR_NONE) {

        AGENT_LOG(
            "Failed to enable "
            "MONITOR_CONTENDED_ENTER, err="
            << e1
        );

        return;
    }


    jvmtiError e2 =
        jvmti->SetEventNotificationMode(
            JVMTI_ENABLE,
            JVMTI_EVENT_MONITOR_CONTENDED_ENTERED,
            nullptr
        );


    if (e2 != JVMTI_ERROR_NONE) {

        AGENT_LOG(
            "Failed to enable "
            "MONITOR_CONTENDED_ENTERED, err="
            << e2
        );

        return;
    }


    /*
     * WAIT / WAITED intentionally disabled.
     */

    AGENT_LOG(
        "Monitor Events Enabled"
    );


    AGENT_LOG(
        "Agent Ready — pure event collector mode "
        "(no native deadlock detection)"
    );
}


/*
 * ============================================================
 * SHUTDOWN
 * ============================================================
 */

JNIEXPORT void JNICALL
Java_com_virtual_1deadlock_1detection_LockDetectionAgent_jvmti_JvmtiBridge_shutdownAgent
(
        JNIEnv* env,
        jobject obj) {


    /*
     * Delete the global Java bridge reference.
     */

    if (g_bridgeClass != nullptr) {

        env->DeleteGlobalRef(
            g_bridgeClass
        );

        g_bridgeClass = nullptr;
    }


    /*
     * Clear cached method IDs.
     */

    g_publishLockOwnedId = nullptr;

    g_publishLockReleasedId = nullptr;


    /*
     * Clear JVMTI state.
     */

    jvmti = nullptr;

    g_jvm = nullptr;


    AGENT_LOG(
        "Agent Shutdown"
    );
}
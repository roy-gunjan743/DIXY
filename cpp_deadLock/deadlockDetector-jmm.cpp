#include <jvmti.h>
#include <jni.h>

#include <stdio.h>


// Does not work.
// This function is not exported from libjvm.so .
extern "C" jobjectArray jmm_FindMonitorDeadlockedThreads(JNIEnv *env);


void JNICALL OnMonitorContendedEnterForDeadlock(
                 jvmtiEnv *jvmti, JNIEnv *env, jthread thread, jobject monitor){
  // Find owner thread
  jvmtiMonitorUsage monitor_usage;
  jvmti->GetObjectMonitorUsage(monitor, &monitor_usage);
  jvmti->Deallocate((unsigned char *)monitor_usage.waiters);
  jvmti->Deallocate((unsigned char *)monitor_usage.notify_waiters);

  if(monitor_usage.owner == NULL){ // Monitor has been released
    return;
  }

  // Check dead lock (compare with oop)
  if(*(void **)monitor_usage.owner == *(void **)thread){ // unlikely condition
    jvmtiThreadInfo thread_info;
    jvmti->GetThreadInfo(thread, &thread_info);
    printf("deadlock detected!: %s\n", thread_info.name);
    jvmti->Deallocate((unsigned char *)thread_info.name);
    return;
  }

  // Check thread state of owner thread
  jint thread_state;
  jvmti->GetThreadState(monitor_usage.owner, &thread_state);
  if((thread_state & JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER) == 0){
    // No dead lock because owner thread does not wait at the monitor.
    return;
  }

  // Find dead lock at VM operation
  jobjectArray dl_threads = jmm_FindMonitorDeadlockedThreads(env);
  if(env->GetArrayLength(dl_threads) > 0){
    printf("deadlock detected!\n");
  }

}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved){
  jvmtiEnv *jvmti;
  vm->GetEnv((void **)&jvmti, JVMTI_VERSION_1);

  jvmtiCapabilities capabilities = {0};
  capabilities.can_generate_monitor_events = 1;
  capabilities.can_get_monitor_info = 1;
  jvmti->AddCapabilities(&capabilities);

  jvmtiEventCallbacks callbacks = {0};
  callbacks.MonitorContendedEnter = &OnMonitorContendedEnterForDeadlock;
  jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));

  jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                                  JVMTI_EVENT_MONITOR_CONTENDED_ENTER, NULL);
  printf("Agent started.\n");
  return JNI_OK;
}

JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm){
  // Do nothing
  
}
#include <jvmti.h>
#include <jni.h>

#include <stdio.h>


static jlong next_tag = 1;

void JNICALL OnMonitorContendedEnterForDeadlock(
                 jvmtiEnv *jvmti, JNIEnv *env, jthread thread, jobject monitor){
  // Get or set unique tag for the monitor object (even tags only)
  jlong monitor_tag = 0;
  jvmti->GetTag(monitor, &monitor_tag);
  if (monitor_tag == 0) {
    monitor_tag = next_tag * 2;
    next_tag++;
    jvmti->SetTag(monitor, monitor_tag);
  }

  // Set the thread's tag to monitor_tag + 1 (always odd) to indicate what it is waiting for
  jvmti->SetTag(thread, monitor_tag + 1);

  // Traverse the dependency chain to check for deadlock
  jobject current_monitor = monitor;
  while (current_monitor != NULL) {
    jvmtiMonitorUsage usage;
    jvmtiError err = jvmti->GetObjectMonitorUsage(current_monitor, &usage);
    if (err != JVMTI_ERROR_NONE) {
      break;
    }

    jthread owner = usage.owner;
    jvmti->Deallocate((unsigned char *)usage.waiters);
    jvmti->Deallocate((unsigned char *)usage.notify_waiters);

    if (owner == NULL) {
      break;
    }

    // Check for deadlock using JNI IsSameObject
    if (env->IsSameObject(owner, thread)) {
      jvmtiThreadInfo thread_info;
      if (jvmti->GetThreadInfo(thread, &thread_info) == JVMTI_ERROR_NONE) {
        printf("deadlock detected!: %s\n", thread_info.name);
        jvmti->Deallocate((unsigned char *)thread_info.name);
      } else {
        printf("deadlock detected!\n");
      }
      fflush(stdout);
      break;
    }

    // Get the tag of the owner thread
    jlong owner_tag = 0;
    jvmti->GetTag(owner, &owner_tag);
    
    // An owner tag of 0 or an even tag means the owner thread is not currently contending
    if (owner_tag == 0 || (owner_tag % 2 == 0)) {
      break;
    }

    // The contended monitor's tag is always (owner_tag - 1)
    jlong target_monitor_tag = owner_tag - 1;

    // Find the monitor object associated with target_monitor_tag (guaranteed to return only the monitor)
    jint count = 0;
    jobject* objects = NULL;
    jlong* tags = NULL;
    err = jvmti->GetObjectsWithTags(1, &target_monitor_tag, &count, &objects, &tags);
    if (err != JVMTI_ERROR_NONE || count == 0) {
      if (objects != NULL) jvmti->Deallocate((unsigned char *)objects);
      if (tags != NULL) jvmti->Deallocate((unsigned char *)tags);
      break;
    }

    current_monitor = objects[0];
    jvmti->Deallocate((unsigned char *)objects);
    jvmti->Deallocate((unsigned char *)tags);
  }
}

void JNICALL OnMonitorContendedEnteredForDeadlock(
                 jvmtiEnv *jvmti, JNIEnv *env, jthread thread, jobject monitor){
  // Clear the thread's tag when it successfully enters the monitor
  jvmti->SetTag(thread, 0L);
}

JNIEXPORT jint JNICALL Agent_OnLoad(JavaVM *vm, char *options, void *reserved){
  // Unbuffer stdout to ensure immediate outputs when running in background tasks/redirection
  setvbuf(stdout, NULL, _IONBF, 0);

  jvmtiEnv *jvmti;
  vm->GetEnv((void **)&jvmti, JVMTI_VERSION_1);

  jvmtiCapabilities capabilities = {0};
  capabilities.can_generate_monitor_events = 1;
  capabilities.can_tag_objects = 1;
  capabilities.can_get_monitor_info = 1;
  jvmti->AddCapabilities(&capabilities);

  jvmtiEventCallbacks callbacks = {0};
  callbacks.MonitorContendedEnter = &OnMonitorContendedEnterForDeadlock;
  callbacks.MonitorContendedEntered = &OnMonitorContendedEnteredForDeadlock;
  jvmti->SetEventCallbacks(&callbacks, sizeof(jvmtiEventCallbacks));

  jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                                  JVMTI_EVENT_MONITOR_CONTENDED_ENTER, NULL);
  jvmti->SetEventNotificationMode(JVMTI_ENABLE,
                                  JVMTI_EVENT_MONITOR_CONTENDED_ENTERED, NULL);

  printf("Agent started.\n");
  return JNI_OK;
}

JNIEXPORT void JNICALL Agent_OnUnload(JavaVM *vm){
  // Do nothing
}
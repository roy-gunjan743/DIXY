package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.enums.ThreadState;
import com.virtual_deadlock_detection.backend.repository.ThreadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ThreadService {

    private final ThreadRepository threadRepository;

    public ThreadService(ThreadRepository threadRepository) {
        this.threadRepository = threadRepository;
    }

    public ThreadEntity saveThread(ThreadEntity thread) {
        return threadRepository.save(thread);
    }

    public List<ThreadEntity> getAllThreads() {
        return threadRepository.findAll();
    }

    /**
     * Instance and app-scoped thread lookup.
     */
    public Optional<ThreadEntity> getThreadByThreadIdAndAppNameAndInstanceId(
            Long threadId,
            String appName,
            String instanceId) {
        return threadRepository.findByThreadIdAndAppNameAndInstanceId(
                threadId,
                appName == null || appName.isBlank() ? "unknown-app" : appName,
                instanceId == null || instanceId.isBlank() ? "unknown" : instanceId
        );
    }

    /**
     * Instance-scoped thread lookup.
     */
    public Optional<ThreadEntity> getThreadByThreadIdAndInstanceId(
            Long threadId,
            String instanceId) {
        return threadRepository.findByThreadIdAndInstanceId(
                threadId,
                instanceId == null || instanceId.isBlank() ? "unknown" : instanceId
        );
    }

    /**
     * @deprecated Use {@link #getThreadByThreadIdAndAppNameAndInstanceId(Long, String, String)} instead.
     */
    @Deprecated
    public Optional<ThreadEntity> getThreadByThreadId(Long threadId) {
        return threadRepository.findByThreadId(threadId);
    }

    public void deleteThread(Long id) {
        threadRepository.deleteById(id);
    }

    /**
     * Register a new thread or update an existing thread.
     *
     * Scoped to the composite identity (threadId + appName + instanceId).
     * Database-level uniqueness (uq_threads_thread_id_app_name_instance_id)
     * is the authoritative guard across multiple backend pods and concurrent threads.
     */
    @Transactional
    public ThreadEntity registerOrUpdateThread(
            ThreadEntity thread) {

        if (thread == null) {
            throw new IllegalArgumentException(
                    "Thread cannot be null"
            );
        }

        if (thread.getThreadId() == null) {
            throw new IllegalArgumentException(
                    "Cannot register thread without a threadId"
            );
        }

        String appName = thread.getAppName() == null || thread.getAppName().isBlank()
                ? "unknown-app" : thread.getAppName();
        String instanceId = thread.getInstanceId() == null || thread.getInstanceId().isBlank()
                ? "unknown" : thread.getInstanceId();
        String threadName = thread.getThreadName() == null || thread.getThreadName().isBlank()
                ? "unknown-thread-" + thread.getThreadId() : thread.getThreadName();
        String state = thread.getState() == null ? "RUNNABLE" : thread.getState().name();
        String carrierThread = thread.getCarrierThread();

        threadRepository.upsertThread(
                thread.getThreadId(),
                appName,
                instanceId,
                threadName,
                state,
                carrierThread
        );

        return threadRepository.findByThreadIdAndAppNameAndInstanceId(
                thread.getThreadId(),
                appName,
                instanceId
        ).orElse(thread);
    }

    /**
     * Kept for ThreadController compatibility.
     */
    public ThreadEntity registerThread(ThreadEntity thread) {
        return registerOrUpdateThread(thread);
    }
}
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

    public Optional<ThreadEntity> getThreadByThreadId(Long threadId) {
        return threadRepository.findByThreadId(threadId);
    }

    public void deleteThread(Long id) {
        threadRepository.deleteById(id);
    }

    /**
     * Register a new thread or update an existing thread.
     *
     * synchronized is important because the agent can send
     * multiple telemetry requests for the same JVM thread
     * at almost the same time.
     */
    @Transactional
    public synchronized ThreadEntity registerOrUpdateThread(
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

        if (thread.getAppName() == null
                || thread.getAppName().isBlank()) {

            thread.setAppName("unknown-app");
        }

        if (thread.getThreadName() == null
                || thread.getThreadName().isBlank()) {

            thread.setThreadName(
                    "unknown-thread-" + thread.getThreadId()
            );
        }

        Optional<ThreadEntity> existing =
                threadRepository.findByThreadIdAndAppName(
                        thread.getThreadId(),
                        thread.getAppName()
                );

        /*
         * Thread already exists.
         * Update only the information that is actually present.
         */
        if (existing.isPresent()) {

            ThreadEntity current = existing.get();

            if (thread.getThreadName() != null
                    && !thread.getThreadName().isBlank()) {

                current.setThreadName(
                        thread.getThreadName()
                );
            }

            /*
             * Never replace a valid state with null.
             */
            if (thread.getState() != null) {
                current.setState(thread.getState());
            }

            if (thread.getCarrierThread() != null
                    && !thread.getCarrierThread().isBlank()) {

                current.setCarrierThread(
                        thread.getCarrierThread()
                );
            }

            return threadRepository.save(current);
        }

        /*
         * New thread.
         *
         * The database requires state to be NOT NULL,
         * so give a new thread a safe initial state when
         * the first telemetry event doesn't contain one.
         */
        if (thread.getState() == null) {
            thread.setState(ThreadState.RUNNABLE);
        }

        return threadRepository.save(thread);
    }

    /**
     * Kept for ThreadController compatibility.
     */
    public ThreadEntity registerThread(ThreadEntity thread) {
        return registerOrUpdateThread(thread);
    }
}
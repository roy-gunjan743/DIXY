package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
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

    /**
     * Register a thread if it does not already exist.
     *
     * Thread identity is:
     *
     *     threadId + appName
     *
     * because the database uses the same composite uniqueness rule.
     */
    @Transactional
    public ThreadEntity registerThread(ThreadEntity thread) {

        Optional<ThreadEntity> existing =
                threadRepository.findByThreadIdAndAppName(
                        thread.getThreadId(),
                        thread.getAppName()
                );

        if (existing.isPresent()) {
            return existing.get();
        }

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
     * Update an existing thread or create a new one.
     *
     * IMPORTANT:
     *
     * We do not replace an existing state with null.
     */
    @Transactional
    public ThreadEntity registerOrUpdateThread(ThreadEntity thread) {

        Optional<ThreadEntity> existing =
                threadRepository.findByThreadIdAndAppName(
                        thread.getThreadId(),
                        thread.getAppName()
                );

        if (existing.isPresent()) {

            ThreadEntity current = existing.get();

            if (thread.getThreadName() != null &&
                    !thread.getThreadName().isBlank()) {

                current.setThreadName(
                        thread.getThreadName()
                );
            }

            /*
             * Never destroy a previously known state
             * because an incoming event has no state.
             */
            if (thread.getState() != null) {
                current.setState(
                        thread.getState()
                );
            }

            if (thread.getCarrierThread() != null) {
                current.setCarrierThread(
                        thread.getCarrierThread()
                );
            }

            return threadRepository.save(current);
        }

        /*
         * New thread must have a valid state because
         * ThreadEntity.state is NOT NULL.
         *
         * TelemetryService normally resolves this before
         * calling this method.
         */
        if (thread.getState() == null) {
            throw new IllegalArgumentException(
                    "Cannot register thread without a state: threadId="
                            + thread.getThreadId()
                            + ", appName="
                            + thread.getAppName()
            );
        }

        return threadRepository.save(thread);
    }
}
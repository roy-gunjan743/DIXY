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

    @Transactional
    public ThreadEntity registerOrUpdateThread(ThreadEntity thread) {

        if (thread.getThreadId() == null) {
            throw new IllegalArgumentException(
                    "Cannot register thread without a threadId"
            );
        }

        if (thread.getAppName() == null || thread.getAppName().isBlank()) {
            thread.setAppName("unknown-app");
        }

        if (thread.getThreadName() == null || thread.getThreadName().isBlank()) {
            thread.setThreadName("unknown-thread-" + thread.getThreadId());
        }

        Optional<ThreadEntity> existing =
                threadRepository.findByThreadIdAndAppName(
                        thread.getThreadId(),
                        thread.getAppName()
                );

        if (existing.isPresent()) {

            ThreadEntity current = existing.get();

            if (thread.getThreadName() != null
                    && !thread.getThreadName().isBlank()) {
                current.setThreadName(thread.getThreadName());
            }

            /*
             * Do NOT overwrite a valid state with null.
             */
            if (thread.getState() != null) {
                current.setState(thread.getState());
            }

            if (thread.getCarrierThread() != null
                    && !thread.getCarrierThread().isBlank()) {
                current.setCarrierThread(thread.getCarrierThread());
            }

            return threadRepository.save(current);
        }

        /*
         * A new thread must always have a valid state because
         * threads.state is NOT NULL in the database.
         *
         * If the first telemetry event doesn't contain a state,
         * RUNNABLE is the safest initial state.
         */
        if (thread.getState() == null) {
            thread.setState(ThreadState.RUNNABLE);
        }

        return threadRepository.save(thread);
    }
    public ThreadEntity registerThread(ThreadEntity thread) {
        return registerOrUpdateThread(thread);
    }
}
package com.virtual_deadlock_detection.backend.service;

import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.repository.ThreadRepository;
import org.springframework.stereotype.Service;

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

    public ThreadEntity registerThread(ThreadEntity thread) {

        Optional<ThreadEntity> existing =
                threadRepository.findByThreadId(thread.getThreadId());

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


    public ThreadEntity registerOrUpdateThread(ThreadEntity thread) {

        return threadRepository.findByThreadId(thread.getThreadId())
                .map(existing -> {

                    existing.setThreadName(thread.getThreadName());
                    existing.setState(thread.getState());
                    existing.setCarrierThread(thread.getCarrierThread());

                    return threadRepository.save(existing);

                })
                .orElseGet(() -> threadRepository.save(thread));
    }

}
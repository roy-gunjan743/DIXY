package com.virtual_deadlock_detection.backend.controller;

import com.virtual_deadlock_detection.backend.entity.ThreadEntity;
import com.virtual_deadlock_detection.backend.service.ThreadService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/threads")
@CrossOrigin(origins = "*")
public class ThreadController {

    private final ThreadService threadService;

    public ThreadController(ThreadService threadService) {
        this.threadService = threadService;
    }

    @PostMapping
    public ThreadEntity createThread(@RequestBody ThreadEntity thread) {
        return threadService.registerThread(thread);
    }

    @GetMapping
    public List<ThreadEntity> getAllThreads() {
        return threadService.getAllThreads();
    }

    @GetMapping("/{threadId}")
    public ThreadEntity getThread(@PathVariable Long threadId) {
        return threadService.getThreadByThreadId(threadId)
                .orElseThrow(() -> new RuntimeException("Thread not found"));
    }

    @DeleteMapping("/{id}")
    public void deleteThread(@PathVariable Long id) {
        threadService.deleteThread(id);
    }
}
package com.virtual_deadlock_detection.graph.registry;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class ThreadRegistry {

    private final ConcurrentHashMap<Long, ThreadInfo> threads =
            new ConcurrentHashMap<>();

    public void register(ThreadInfo thread) {
        threads.put(thread.getThreadId(), thread);
    }

    public ThreadInfo get(long threadId) {
        return threads.get(threadId);
    }

    public void unregister(long threadId) {
        threads.remove(threadId);
    }

    public boolean contains(long threadId) {
        return threads.containsKey(threadId);
    }

    public Collection<ThreadInfo> getAllThreads() {
        return threads.values();
    }

    public void updateState(long threadId, Thread.State state) {

        ThreadInfo info = threads.get(threadId);

        if (info != null) {
            info.setState(state);
        }

    }

    public int size() {
        return threads.size();
    }

    public void clear() {
        threads.clear();
    }

}
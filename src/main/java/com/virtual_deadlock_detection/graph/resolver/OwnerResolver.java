package com.virtual_deadlock_detection.graph.resolver;

import com.virtual_deadlock_detection.graph.registry.LockInfo;
import com.virtual_deadlock_detection.graph.registry.LockRegistry;
import com.virtual_deadlock_detection.graph.registry.WaitingInfo;
import com.virtual_deadlock_detection.graph.registry.WaitingRegistry;

import java.util.ArrayList;
import java.util.List;

public class OwnerResolver {

    private final LockRegistry lockRegistry;
    private final WaitingRegistry waitingRegistry;

    public OwnerResolver(LockRegistry lockRegistry,
                         WaitingRegistry waitingRegistry) {

        this.lockRegistry = lockRegistry;
        this.waitingRegistry = waitingRegistry;
    }

    public List<OwnerEdge> resolve() {

        List<OwnerEdge> edges = new ArrayList<>();

        for (WaitingInfo waiting : waitingRegistry.getAllWaitingThreads()) {

            LockInfo owner =
                    lockRegistry.get(waiting.getMonitorId());

            if (owner == null)
                continue;

            // Ignore self-loop
            if (owner.getOwnerThreadId() == waiting.getThreadId())
                continue;

            edges.add(
                    new OwnerEdge(
                            waiting.getThreadId(),
                            owner.getOwnerThreadId()
                    )
            );
        }

        return edges;
    }

}
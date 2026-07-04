package com.virtual_deadlock_detection.graph.engine;

import com.virtual_deadlock_detection.graph.model.EdgeType;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;
import com.virtual_deadlock_detection.graph.model.NodeType;
import com.virtual_deadlock_detection.graph.registry.ThreadInfo;
import com.virtual_deadlock_detection.graph.registry.ThreadRegistry;
import com.virtual_deadlock_detection.graph.resolver.OwnerEdge;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GraphBuilder {

    private final ThreadRegistry threadRegistry;

    public GraphBuilder(ThreadRegistry threadRegistry) {
        this.threadRegistry = threadRegistry;
    }

    public Set<GraphEdge> build(List<OwnerEdge> ownerEdges) {

        Set<GraphEdge> graphEdges = new HashSet<>();

        for (OwnerEdge edge : ownerEdges) {

            ThreadInfo waiting =
                    threadRegistry.get(edge.getWaitingThreadId());

            ThreadInfo owner =
                    threadRegistry.get(edge.getOwnerThreadId());

            if (waiting == null || owner == null)
                continue;

            GraphNode source =
                    new GraphNode(
                            waiting.getThreadId(),
                            NodeType.VIRTUAL_THREAD,
                            waiting.getThreadName()
                    );

            GraphNode target =
                    new GraphNode(
                            owner.getThreadId(),
                            NodeType.VIRTUAL_THREAD,
                            owner.getThreadName()
                    );

            graphEdges.add(
                    new GraphEdge(
                            source,
                            target,
                            EdgeType.WAITS_FOR
                    )
            );
        }

        return graphEdges;
    }

}
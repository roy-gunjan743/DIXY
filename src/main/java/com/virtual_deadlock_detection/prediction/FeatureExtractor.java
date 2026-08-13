package com.virtual_deadlock_detection.prediction;

import com.virtual_deadlock_detection.graph.model.EdgeType;
import com.virtual_deadlock_detection.graph.model.GraphEdge;
import com.virtual_deadlock_detection.graph.model.GraphNode;
import com.virtual_deadlock_detection.graph.model.NodeType;
import com.virtual_deadlock_detection.graph.registry.WaitForGraph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FeatureExtractor {

    public FeatureVector extract(WaitForGraph graph) {

        Map<GraphNode, Set<GraphEdge>> edges = graph.getEdges();

        List<Long> waitsForAges = new ArrayList<>();

        int waitsForEdgeCount = 0;

        /*
         * Count the real WAITS_FOR edges currently present
         * in the live WaitForGraph.
         */
        for (Set<GraphEdge> outgoingEdges : edges.values()) {

            for (GraphEdge edge : outgoingEdges) {

                if (edge.getType() == EdgeType.WAITS_FOR) {

                    waitsForEdgeCount++;

                    waitsForAges.add(edge.ageMicros());
                }
            }
        }

        /*
         * Calculate real edge-age statistics.
         */
        double averageEdgeAgeMicros = 0.0;
        double maximumEdgeAgeMicros = 0.0;
        double minimumEdgeAgeMicros = 0.0;

        if (!waitsForAges.isEmpty()) {

            long totalAge = 0;
            long minimumAge = Long.MAX_VALUE;
            long maximumAge = Long.MIN_VALUE;

            for (long age : waitsForAges) {

                totalAge += age;

                minimumAge = Math.min(minimumAge, age);
                maximumAge = Math.max(maximumAge, age);
            }

            averageEdgeAgeMicros =
                    (double) totalAge / waitsForAges.size();

            minimumEdgeAgeMicros = minimumAge;
            maximumEdgeAgeMicros = maximumAge;
        }

        /*
         * Calculate the longest real dependency chain.
         */
        int waitChainLength =
                calculateWaitChainLength(graph);

        /*
         * Count unique virtual threads that currently
         * have a real WAITS_FOR relationship.
         */
        int blockedThreadCount =
                calculateBlockedThreadCount(graph);

        /*
         * Build the feature vector entirely from values
         * extracted from the real WaitForGraph.
         */
        return new FeatureVector(
                waitsForEdgeCount,
                averageEdgeAgeMicros,
                maximumEdgeAgeMicros,
                minimumEdgeAgeMicros,
                waitChainLength,
                blockedThreadCount
        );
    }

    /**
     * Calculates the longest thread-to-thread dependency
     * chain currently present in the WaitForGraph.
     *
     * Dependency pattern:
     *
     * VIRTUAL_THREAD
     *       ↓ WAITS_FOR
     * LOCK
     *       ↓ HOLDS
     * VIRTUAL_THREAD
     */
    private int calculateWaitChainLength(WaitForGraph graph) {

        int maximumChainLength = 0;

        for (GraphNode node : graph.getNodes()) {

            if (node.getType() != NodeType.VIRTUAL_THREAD) {
                continue;
            }

            int chainLength =
                    calculateChainFromThread(
                            node,
                            graph,
                            new HashSet<>()
                    );

            maximumChainLength =
                    Math.max(
                            maximumChainLength,
                            chainLength
                    );
        }

        return maximumChainLength;
    }

    /**
     * Recursively follows:
     *
     * Thread → WAITS_FOR → Lock → HOLDS → Thread
     *
     * and calculates the longest dependency chain.
     */
    private int calculateChainFromThread(
            GraphNode thread,
            WaitForGraph graph,
            Set<GraphNode> path
    ) {

        /*
         * Prevent infinite recursion when the graph
         * contains a circular dependency.
         */
        if (!path.add(thread)) {
            return 0;
        }

        int maximumDepth = 0;

        Set<GraphEdge> outgoingEdges =
                graph.getEdges().getOrDefault(
                        thread,
                        Set.of()
                );

        /*
         * Find the locks this thread is waiting for.
         */
        for (GraphEdge waitEdge : outgoingEdges) {

            if (waitEdge.getType() != EdgeType.WAITS_FOR) {
                continue;
            }

            GraphNode lock =
                    waitEdge.getTarget();

            Set<GraphEdge> lockEdges =
                    graph.getEdges().getOrDefault(
                            lock,
                            Set.of()
                    );

            /*
             * Find the thread holding the lock.
             */
            for (GraphEdge holdEdge : lockEdges) {

                if (holdEdge.getType() != EdgeType.HOLDS) {
                    continue;
                }

                GraphNode nextThread =
                        holdEdge.getTarget();

                if (nextThread.getType()
                        != NodeType.VIRTUAL_THREAD) {
                    continue;
                }

                /*
                 * One real dependency transition:
                 *
                 * Current Thread
                 *      ↓
                 *     Lock
                 *      ↓
                 * Next Thread
                 */
                int depth =
                        1 + calculateChainFromThread(
                                nextThread,
                                graph,
                                new HashSet<>(path)
                        );

                maximumDepth =
                        Math.max(
                                maximumDepth,
                                depth
                        );
            }
        }

        return maximumDepth;
    }

    /**
     * Counts unique virtual threads that currently have
     * at least one real WAITS_FOR edge.
     *
     * Multiple WAITS_FOR edges from the same thread
     * still count as one blocked thread.
     */
    private int calculateBlockedThreadCount(
            WaitForGraph graph
    ) {

        Set<GraphNode> blockedThreads =
                new HashSet<>();

        for (Map.Entry<GraphNode, Set<GraphEdge>> entry
                : graph.getEdges().entrySet()) {

            GraphNode source =
                    entry.getKey();

            /*
             * Only virtual threads can contribute to
             * this blocked-thread feature.
             */
            if (source.getType()
                    != NodeType.VIRTUAL_THREAD) {
                continue;
            }

            for (GraphEdge edge : entry.getValue()) {

                if (edge.getType()
                        == EdgeType.WAITS_FOR) {

                    blockedThreads.add(source);

                    /*
                     * We only need to know whether this
                     * thread is blocked. Additional
                     * WAITS_FOR edges do not increase
                     * the thread count.
                     */
                    break;
                }
            }
        }

        return blockedThreads.size();
    }
}
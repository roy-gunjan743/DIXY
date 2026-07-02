package com.virtual_deadlock_detection.graph.model;

/**
 * Type of a node in the Wait-For Graph.
 * VIRTUAL_THREAD nodes represent lightweight virtual threads.
 * CARRIER_THREAD nodes represent platform threads or JVM carrier threads.
 * LOCK nodes represent monitors, ReentrantLocks, or any tracked resource.
 */
public enum NodeType {
    VIRTUAL_THREAD,
    CARRIER_THREAD,
    LOCK
}
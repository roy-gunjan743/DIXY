package com.virtual_deadlock_detection.graph.model;

/**
 * Semantic meaning of a directed edge in the Wait-For Graph.
 */
public enum EdgeType {
    /** source (thread) is waiting for target (lock) */
    WAITS_FOR,
    /** source (lock) is held by target (thread) */
    HELD_BY,
    /** source (virtual thread) pins target (carrier thread) */
    PINS
}

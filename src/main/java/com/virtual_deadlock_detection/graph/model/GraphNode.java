package com.virtual_deadlock_detection.graph.model;

import java.util.Objects;

/**
 * Represents a vertex in the Wait-For Graph.
 * Can be a Virtual Thread, a Carrier Thread, or a Lock.
 */
public final class GraphNode {

    private final long id;
    private final NodeType type;
    private final String label;

    public GraphNode(long id, NodeType type, String label) {
        this.id = id;
        this.type = Objects.requireNonNull(type, "type");
        // Fallback to a default name if no label is provided
        this.label = label != null ? label : type.name() + "-" + id;
    }

    public long getId() {
        return id;
    }

    public NodeType getType() {
        return type;
    }

    /** Required by DeadlockReport.java */
    public String getLabel() {
        return label;
    }

    /** Required by DeadlockReportGenerator.java */
    public boolean isThread() {
        // Returns true for both virtual and carrier threads
        return type.name().endsWith("THREAD");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GraphNode)) return false;
        GraphNode graphNode = (GraphNode) o;
        return id == graphNode.id && type == graphNode.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type);
    }

    @Override
    public String toString() {
        return label;
    }
}
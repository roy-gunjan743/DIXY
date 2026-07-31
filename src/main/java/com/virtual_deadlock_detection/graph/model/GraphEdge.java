package com.virtual_deadlock_detection.graph.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A directed, timestamped edge between two {@link GraphNode}s.
 * Immutable value object - edges are replaced, not mutated, whenever
 * a wait/hold relationship changes.
 */
public final class GraphEdge {

    private final GraphNode source;
    private final GraphNode target;
    private final EdgeType type;
    private final Instant since;

    public GraphEdge(GraphNode source, GraphNode target, EdgeType type) {
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
        this.type = Objects.requireNonNull(type, "type");
        this.since = Instant.now();
    }

    public GraphNode getSource() {
        return source;
    }

    public GraphNode getTarget() {
        return target;
    }

    public EdgeType getType() {
        return type;
    }

    public Instant getSince() {
        return since;
    }

    /** Microseconds this edge has existed - used against the pinning/deadlock threshold. */
    public long ageMicros() {
        return java.time.Duration.between(since, Instant.now()).toNanos() / 1000L;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GraphEdge)) return false;
        GraphEdge edge = (GraphEdge) o;
        return source.equals(edge.source) && target.equals(edge.target) && type == edge.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, type);
    }

    @Override
    public String toString() {
        return source + " --[" + type + "]--> " + target;
    }


}
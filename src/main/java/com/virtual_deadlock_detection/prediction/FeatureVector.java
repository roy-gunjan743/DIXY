package com.virtual_deadlock_detection.prediction;

public class FeatureVector {

    private final int waitsForEdgeCount;
    private final double averageEdgeAgeMicros;
    private final double maximumEdgeAgeMicros;
    private final double minimumEdgeAgeMicros;

    public FeatureVector(
            int waitsForEdgeCount,
            double averageEdgeAgeMicros,
            double maximumEdgeAgeMicros,
            double minimumEdgeAgeMicros
    ) {
        this.waitsForEdgeCount = waitsForEdgeCount;
        this.averageEdgeAgeMicros = averageEdgeAgeMicros;
        this.maximumEdgeAgeMicros = maximumEdgeAgeMicros;
        this.minimumEdgeAgeMicros = minimumEdgeAgeMicros;
    }

    public int getWaitsForEdgeCount() {
        return waitsForEdgeCount;
    }

    public double getAverageEdgeAgeMicros() {
        return averageEdgeAgeMicros;
    }

    public double getMaximumEdgeAgeMicros() {
        return maximumEdgeAgeMicros;
    }

    public double getMinimumEdgeAgeMicros() {
        return minimumEdgeAgeMicros;
    }
}

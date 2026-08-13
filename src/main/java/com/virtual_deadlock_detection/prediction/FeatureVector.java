package com.virtual_deadlock_detection.prediction;

public class FeatureVector {

    private final int waitsForEdgeCount;
    private final double averageEdgeAgeMicros;
    private final double maximumEdgeAgeMicros;
    private final double minimumEdgeAgeMicros;
    private final int waitChainLength;
    private final int blockedThreadCount;

    public FeatureVector(
            int waitsForEdgeCount,
            double averageEdgeAgeMicros,
            double maximumEdgeAgeMicros,
            double minimumEdgeAgeMicros,
            int waitChainLength,
            int blockedThreadCount
    ) {
        this.waitsForEdgeCount = waitsForEdgeCount;
        this.averageEdgeAgeMicros = averageEdgeAgeMicros;
        this.maximumEdgeAgeMicros = maximumEdgeAgeMicros;
        this.minimumEdgeAgeMicros = minimumEdgeAgeMicros;
        this.waitChainLength = waitChainLength;
        this.blockedThreadCount = blockedThreadCount;
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

    public int getWaitChainLength() {
        return waitChainLength;
    }

    public int getBlockedThreadCount() {
        return blockedThreadCount;
    }
}
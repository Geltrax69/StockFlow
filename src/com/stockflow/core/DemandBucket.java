package com.stockflow.core;

/**
 * DemandBucket — sliding window of recent demand samples.
 * Keeps the last `windowSize` daily demand values so the engine can compute
 * moving average and a velocity (slope) cheaply.
 */
public class DemandBucket {
    private final int windowSize;
    private final int[] ring;
    private int head = 0;
    private int filled = 0;

    public DemandBucket(int windowSize) {
        this.windowSize = windowSize;
        this.ring = new int[windowSize];
    }

    public void add(int demand) {
        ring[head] = demand;
        head = (head + 1) % windowSize;
        if (filled < windowSize) filled++;
    }

    public double movingAverage() {
        if (filled == 0) return 0;
        long sum = 0;
        for (int i = 0; i < filled; i++) sum += ring[i];
        return (double) sum / filled;
    }

    /** Slope per day using first vs second half of the window. Positive = rising. */
    public double velocity() {
        if (filled < 2) return 0;
        int half = filled / 2;
        long first = 0, second = 0;
        int idx = (head - filled + windowSize) % windowSize;
        for (int i = 0; i < half; i++) { first += ring[idx]; idx = (idx + 1) % windowSize; }
        for (int i = half; i < filled; i++) { second += ring[idx]; idx = (idx + 1) % windowSize; }
        if (half == 0) return 0;
        return ((double) (second - first) / half) / Math.max(1, half);
    }

    public int size() { return filled; }
}

package com.stockflow.models;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Truck — delivery vehicle. Has capacity, speed, and a status that the
 * transportation simulator mutates.
 */
public class Truck {
    private static final AtomicLong ID_GEN = new AtomicLong(1);

    public enum Status { IDLE, EN_ROUTE, BROKEN_DOWN }

    private final String id;
    private final int capacity;
    private final double speedKmPerHour;
    private final double costPerKm;
    private volatile Status status = Status.IDLE;
    private volatile long brokenUntilMs = 0;

    public Truck(int capacity, double speedKmPerKm, double costPerKm) {
        this.id = "T-" + ID_GEN.getAndIncrement();
        this.capacity = capacity;
        this.speedKmPerHour = speedKmPerKm;
        this.costPerKm = costPerKm;
    }

    public String getId() { return id; }
    public int getCapacity() { return capacity; }
    public double getSpeed() { return speedKmPerHour; }
    public double getCostPerKm() { return costPerKm; }
    public Status getStatus() { return status; }
    public long getBrokenUntilMs() { return brokenUntilMs; }

    public boolean isAvailable(long now) {
        if (status == Status.BROKEN_DOWN && now >= brokenUntilMs) {
            status = Status.IDLE;
            brokenUntilMs = 0;
        }
        return status == Status.IDLE;
    }

    public void setEnRoute() { status = Status.EN_ROUTE; }
    public void setIdle() { status = Status.IDLE; }
    public void breakDown(long durationMs, long now) {
        status = Status.BROKEN_DOWN;
        brokenUntilMs = now + durationMs;
    }
}

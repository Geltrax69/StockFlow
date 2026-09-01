package com.stockflow.models;

import java.io.Serializable;
import java.util.Objects;

/**
 * Warehouse — bulk storage hub. Has coordinates for distance math and a
 * capacity for each product (we keep a single capacity here for simplicity).
 */
public class Warehouse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final double x, y; // map coordinates
    private final int maxCapacityPerProduct;
    private boolean operational = true;
    private long failureUntil = 0; // sim time ms

    public Warehouse(String id, String name, double x, double y, int maxCapacityPerProduct) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.maxCapacityPerProduct = maxCapacityPerProduct;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getX() { return x; }
    public double getY() { return y; }
    public int getMaxCapacityPerProduct() { return maxCapacityPerProduct; }

    public boolean isOperational(long now) {
        if (!operational) return now >= failureUntil;
        return true;
    }

    public void fail(long durationMs, long now) {
        this.operational = false;
        this.failureUntil = now + durationMs;
    }

    public void recover() {
        this.operational = true;
        this.failureUntil = 0;
    }

    @Override
    public String toString() { return name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Warehouse)) return false;
        return id.equals(((Warehouse) o).id);
    }
    @Override
    public int hashCode() { return Objects.hash(id); }
}

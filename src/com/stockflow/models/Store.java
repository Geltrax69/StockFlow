package com.stockflow.models;

import java.io.Serializable;
import java.util.Objects;

/**
 * Store — retail outlet. Has priority (gold/silver/bronze) which the
 * optimizer weighs when stocks are scarce.
 */
public class Store implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Priority { GOLD(3), SILVER(2), BRONZE(1);
        final int weight;
        Priority(int w) { this.weight = w; }
        public int weight() { return weight; }
    }

    private final String id;
    private final String name;
    private final double x, y;
    private final Priority priority;
    private boolean operational = true;
    private long closedUntil = 0;

    public Store(String id, String name, double x, double y, Priority priority) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.priority = priority;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getX() { return x; }
    public double getY() { return y; }
    public Priority getPriority() { return priority; }

    public boolean isOperational(long now) {
        if (!operational) return now >= closedUntil;
        return true;
    }
    public void close(long durationMs, long now) {
        operational = false;
        closedUntil = now + durationMs;
    }
    public void reopen() { operational = true; closedUntil = 0; }

    @Override
    public String toString() { return name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Store)) return false;
        return id.equals(((Store) o).id);
    }
    @Override
    public int hashCode() { return Objects.hash(id); }
}

package com.stockflow.models;

/**
 * Inventory — thread-safe stock counter at a single location (warehouse or store).
 * Uses synchronized blocks. Fine for the simulation scale; see ARCHITECTURE.md
 * for the per-product lock upgrade path.
 */
public class Inventory {
    private final String locationId;
    private final String productSku;
    private int stock;
    private int reserved; // allocated for pending transfers/orders
    private int lastWeekSales;
    private int totalReceived;
    private int totalSold;

    public Inventory(String locationId, String productSku, int initialStock) {
        this.locationId = locationId;
        this.productSku = productSku;
        this.stock = initialStock;
        this.reserved = 0;
    }

    public synchronized int getAvailable() {
        return Math.max(0, stock - reserved);
    }

    public synchronized int getStock() { return stock; }
    public synchronized int getReserved() { return reserved; }

    public synchronized boolean reserve(int qty) {
        if (getAvailable() < qty) return false;
        reserved += qty;
        return true;
    }

    public synchronized void release(int qty) {
        reserved = Math.max(0, reserved - qty);
    }

    /** Atomically remove qty from stock. Returns false if not enough. */
    public synchronized boolean consume(int qty) {
        if (stock < qty) return false;
        stock -= qty;
        reserved = Math.max(0, reserved - qty);
        totalSold += qty;
        return true;
    }

    /** Add to stock (incoming transfer, supplier delivery). */
    public synchronized void receive(int qty) {
        if (qty <= 0) return;
        stock += qty;
        totalReceived += qty;
        reserved = Math.max(0, reserved - qty);
    }

    /** Simulate corruption: lose a fraction of stock. */
    public synchronized void corrupt(double fraction) {
        int loss = (int) Math.floor(stock * fraction);
        stock = Math.max(0, stock - loss);
    }

    public synchronized void recordWeeklySales(int sold) {
        this.lastWeekSales = sold;
    }
    public synchronized int getLastWeekSales() { return lastWeekSales; }
    public synchronized int getTotalReceived() { return totalReceived; }
    public synchronized int getTotalSold() { return totalSold; }

    public String getLocationId() { return locationId; }
    public String getProductSku() { return productSku; }
}

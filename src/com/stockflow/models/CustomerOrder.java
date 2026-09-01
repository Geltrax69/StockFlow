package com.stockflow.models;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CustomerOrder — a single purchase event at a store. Lines are productSku -> qty.
 * fulfilled lines reduce inventory; unfulfilled lines count as lost sales.
 */
public class CustomerOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    private static long NEXT_ID = 1;
    public static synchronized long nextId() { return NEXT_ID++; }

    private final long orderId;
    private final String storeId;
    private final long timestampMs;
    private final Map<String, Integer> lines = new LinkedHashMap<>();
    private int lostUnits = 0;
    private double revenue = 0;

    public CustomerOrder(String storeId, long timestampMs) {
        this.orderId = nextId();
        this.storeId = storeId;
        this.timestampMs = timestampMs;
    }

    public void addLine(String sku, int qty) { lines.merge(sku, qty, Integer::sum); }
    public long getOrderId() { return orderId; }
    public String getStoreId() { return storeId; }
    public long getTimestampMs() { return timestampMs; }
    public Map<String, Integer> getLines() { return lines; }
    public int getTotalUnits() { return lines.values().stream().mapToInt(Integer::intValue).sum(); }
    public int getLostUnits() { return lostUnits; }
    public void addLostUnits(int n) { this.lostUnits += n; }
    public double getRevenue() { return revenue; }
    public void addRevenue(double r) { this.revenue += r; }
}

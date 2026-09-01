package com.stockflow.core;

/**
 * Recommendation — engine output. Quantifies why a particular warehouse was picked
 * so the UI/log can show the tradeoffs.
 */
public class Recommendation implements Comparable<Recommendation> {
    public final String sku;
    public final String storeId;
    public final String warehouseId;
    public final int quantity;
    public final double score;          // lower = more urgent / cheaper
    public final double transportCost;
    public final long etaMs;
    public final double daysUntilStockout;
    public final String reason;         // human-readable why this warehouse won

    public Recommendation(String sku, String storeId, String warehouseId, int quantity,
                          double score, double transportCost, long etaMs,
                          double daysUntilStockout, String reason) {
        this.sku = sku;
        this.storeId = storeId;
        this.warehouseId = warehouseId;
        this.quantity = quantity;
        this.score = score;
        this.transportCost = transportCost;
        this.etaMs = etaMs;
        this.daysUntilStockout = daysUntilStockout;
        this.reason = reason;
    }

    @Override
    public int compareTo(Recommendation o) {
        // Most urgent first (lowest daysUntilStockout), then cheapest.
        int c = Double.compare(this.daysUntilStockout, o.daysUntilStockout);
        if (c != 0) return c;
        return Double.compare(this.score, o.score);
    }

    @Override
    public String toString() {
        return String.format("REC %s store=%s wh=%s qty=%d score=%.2f cost=%.2f stockout=%.2fd reason=%s",
                sku, storeId, warehouseId, quantity, score, transportCost, daysUntilStockout, reason);
    }
}

package com.stockflow.engine;

import com.stockflow.core.Recommendation;

/**
 * PlanItem — internal scoring record used by the OptimizationEngine. Kept
 * separate from {@link Recommendation} (which is for the live engine) so the
 * planning pass never accidentally mutates the recommendation UI list.
 */
public class PlanItem implements Comparable<PlanItem> {
    public final String sku;
    public final String storeId;
    public final String warehouseId;
    public final int qty;
    public final double score;
    public final double cost;
    public final long etaMs;
    public final double daysUntilStockout;

    public PlanItem(String sku, String storeId, String warehouseId, int qty,
                    double score, double cost, long etaMs, double daysUntilStockout) {
        this.sku = sku; this.storeId = storeId; this.warehouseId = warehouseId;
        this.qty = qty; this.score = score; this.cost = cost;
        this.etaMs = etaMs; this.daysUntilStockout = daysUntilStockout;
    }

    @Override
    public int compareTo(PlanItem o) {
        int c = Double.compare(this.daysUntilStockout, o.daysUntilStockout);
        if (c != 0) return c;
        return Double.compare(this.score, o.score);
    }

    @Override
    public String toString() {
        return String.format("PLAN %s %s->%s qty=%d score=%.2f cost=%.2f days=%.2f",
                sku, warehouseId, storeId, qty, score, cost, daysUntilStockout);
    }
}

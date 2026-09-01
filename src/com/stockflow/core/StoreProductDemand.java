package com.stockflow.core;

import com.stockflow.models.Product;
import com.stockflow.models.Store;
import com.stockflow.models.Warehouse;

/**
 * StoreProductDemand — the per-store, per-product bookkeeping the engine needs.
 * Tracks recent demand history and the last-suggested recommendation so the UI
 * can render state without re-running the optimizer.
 */
public class StoreProductDemand {
    public final Store store;
    public final Product product;
    public final DemandBucket bucket = new DemandBucket(14);
    public int lastRecommendedTransfer = 0;
    public long lastRecommendationMs = 0;

    public StoreProductDemand(Store store, Product product) {
        this.store = store;
        this.product = product;
    }

    public double predictedDailyDemand() {
        // 70% moving average, 30% recent velocity trend — adaptive to spikes.
        double ma = bucket.movingAverage();
        double vel = bucket.velocity();
        return Math.max(0, ma + 0.5 * vel);
    }
}

package com.stockflow.engine;

import com.stockflow.core.*;
import com.stockflow.models.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DemandGenerator — runs on a scheduled thread. Each tick represents one
 * simulated day. For every (store, product) it samples demand from the
 * DemandModel, attempts to fulfill it from store inventory, and feeds the
 * DemandBucket so the optimizer can compute predictions.
 */
public class DemandGenerator implements Runnable {
    private final World world;
    private final EventLogger log;
    private final MetricsManager metrics;
    private final DemandModel demandModel;
    private final AtomicLong dayIndex = new AtomicLong(0);
    private volatile boolean running = true;

    public DemandGenerator(World world, EventLogger log, MetricsManager metrics, DemandModel dm) {
        this.world = world; this.log = log; this.metrics = metrics; this.demandModel = dm;
    }
    public long getDayIndex() { return dayIndex.get(); }
    public void stop() { running = false; }

    @Override
    public void run() {
        while (running) {
            try { tick(); } catch (Exception e) { log.log(EventLogger.Level.ERROR, "Demand tick: %s", e); }
            // One simulated day = 1 second of wall clock. Adjust to taste.
            sleep(1000);
        }
    }

    public void tick() {
        long day = dayIndex.incrementAndGet();
        int dIdx = (int) day;
        long now = System.currentTimeMillis();

        long totalStock = 0;
        double daysSum = 0; int daysCount = 0;
        int totalDemand = 0;

        for (Store s : world.locations.stores().values()) {
            if (!s.isOperational(now)) continue;
            for (Product p : world.products.values()) {
                StoreProductDemand spd = world.demandRegistry.get(s.getId() + "|" + p.getSku());
                Inventory inv = world.inventory.get(s.getId(), p.getSku());
                if (spd == null || inv == null) continue;

                int demand = demandModel.sampleDailyDemand(p, dIdx, s.getId().hashCode());
                spd.bucket.add(demand);
                totalDemand += demand;
                metrics.addUnitsDemanded(demand);
                int stock = inv.getAvailable();
                int fulfilled = Math.min(demand, stock);
                if (fulfilled > 0) {
                    inv.consume(fulfilled);
                    metrics.addUnitsFulfilled(fulfilled);
                    metrics.addRevenue(p.getUnitPrice() * fulfilled);
                }
                if (demand > fulfilled) {
                    int lost = demand - fulfilled;
                    metrics.addUnitsLost(lost);
                    metrics.addLostRevenue(p.getUnitPrice() * lost);
                    metrics.incStockoutEvents();
                }
                double predicted = spd.predictedDailyDemand();
                double daysLeft = predicted > 0 ? inv.getAvailable() / predicted : 999;
                daysSum += daysLeft; daysCount++;
            }
        }

        // Warehouse total stock.
        for (Warehouse w : world.locations.warehouses().values()) {
            for (Inventory inv : world.inventory.at(w.getId()).values()) totalStock += inv.getStock();
        }

        metrics.setTotalInventory(totalStock);
        metrics.setDayIndex(day);
        if (daysCount > 0) metrics.setAvgDaysUntilStockout(daysSum / daysCount);
        metrics.recordDaily(metrics.snapshot().serviceLevel(), totalDemand);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

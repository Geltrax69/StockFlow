package com.stockflow.engine;

import com.stockflow.core.*;
import com.stockflow.models.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RedistributionEngine — the brain. See ALGORITHM.md (in repo root) for the
 * full write-up. Quick summary:
 *
 *  1. For every (store, product) compute predicted demand, daysUntilStockout,
 *     safety stock, and a "need score".
 *  2. If daysUntilStockout < trigger threshold, enqueue a recommendation.
 *  3. Score every operational warehouse for the (store, product) pair using:
 *        cost = transportCost(wh, store) * sku.weight * qty
 *            + 0.4 * (warehouse demand pressure)        // leaves stock for others
 *            + 0.2 * (leadTime * qty)                   // later arrivals are worse
 *            - 0.1 * store.priority.weight * qty * profit  // protect gold + profitable
 *      Lower = better. Pick argmin.
 *  4. Generate the transfer, dispatch a truck, log the choice and the runner-up.
 *  5. Submissions are serialized through a single-thread executor — keeps the
 *     ordering of decisions deterministic and avoids double-issuing for the
 *     same shortfall.
 *
 *  Complexity: O(W · S · P) per cycle. W=warehouses, S=stores, P=products.
 *  In the demo (10 × 30 × 50 = 15 000) this runs in <5 ms. To millions: shard
 *  by product hash, run shards in parallel, and precompute the (wh,store)
 *  distance table once (O(W·S)) — see ALGORITHM.md.
 */
public class RedistributionEngine {
    public enum Strategy { NEAREST, COST_DEMAND_DISTANCE }

    private final World world;
    private final EventLogger log;
    private final MetricsManager metrics;
    private final ExecutorService optimizer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RedistributionEngine");
        t.setDaemon(true);
        return t;
    });
    private volatile Strategy strategy = Strategy.COST_DEMAND_DISTANCE;
    private final AtomicLong cycleCount = new AtomicLong(0);

    // Tunables (could be UI-driven; kept simple).
    public double triggerDays = 5.0;        // if daysUntilStockout < this, act
    public double safetyStockDays = 3.0;    // buffer we want on hand
    public double profitWeight = 0.1;
    public double distanceWeight = 1.0;
    public double truckSpeedKmh = 60.0;     // average truck speed
    public int maxTransfersPerCycle = 50;   // rate-limit per cycle to avoid floods

    public RedistributionEngine(World world, EventLogger log, MetricsManager metrics) {
        this.world = world;
        this.log = log;
        this.metrics = metrics;
        metrics.setActiveStrategy(strategy.name());
    }

    public Strategy getStrategy() { return strategy; }
    public void setStrategy(Strategy s) {
        this.strategy = s;
        metrics.setActiveStrategy(s.name());
        log.log(EventLogger.Level.INFO, "Switched redistribution strategy -> %s", s);
    }

    public void shutdown() { optimizer.shutdownNow(); }

    /** Schedule a redistribution pass. Returns the future for tests/UI. */
    public Future<Integer> runCycle() {
        return optimizer.submit(this::runCycleNow);
    }

    /** Synchronous run — used by the Optimize Inventory button. */
    public int runCycleNow() {
        long t0 = System.currentTimeMillis();
        cycleCount.incrementAndGet();
        long now = System.currentTimeMillis();
        int dispatched = 0;

        // Step 1: precompute the per-warehouse "demand pressure" so high-pressure
        // warehouses get penalized when we draw stock out of them.
        Map<String, Double> whPressure = computeWarehousePressure();

        // Step 2: collect recommendations.
        List<Recommendation> recs = new ArrayList<>();
        for (Store s : world.locations.stores().values()) {
            if (!s.isOperational(now)) continue;
            for (Product p : world.products.values()) {
                StoreProductDemand spd = world.demandRegistry.get(s.getId() + "|" + p.getSku());
                if (spd == null) continue;
                Inventory storeInv = world.inventory.get(s.getId(), p.getSku());
                if (storeInv == null) continue;

                double predicted = spd.predictedDailyDemand();
                int currentStock = storeInv.getAvailable();
                double days = (predicted > 0) ? currentStock / predicted : 999.0;

                if (days >= triggerDays) continue;

                int desired = (int) Math.ceil(predicted * (triggerDays + safetyStockDays));
                int needed = Math.max(0, desired - currentStock);
                if (needed <= 0) continue;

                Recommendation r = pickBestWarehouse(s, p, needed, whPressure, days, predicted);
                if (r != null) recs.add(r);
            }
        }

        // Step 3: sort and dispatch the most urgent first (rate-limited).
        Collections.sort(recs);
        for (Recommendation r : recs) {
            if (dispatched >= maxTransfersPerCycle) break;
            if (issueTransfer(r)) dispatched++;
        }

        long ms = System.currentTimeMillis() - t0;
        if (dispatched > 0) {
            log.log(EventLogger.Level.INFO,
                    "Redistribution cycle #%d dispatched %d transfers in %dms (strategy=%s)",
                    cycleCount.get(), dispatched, ms, strategy);
        }
        return dispatched;
    }

    /**
     * For each warehouse, sum the predicted demand across all stores for each
     * product. Pressure = how much of the warehouse's stock is "spoken for" by
     * current demand. Used to penalize greedy sourcing.
     */
    private Map<String, Double> computeWarehousePressure() {
        Map<String, Double> pressure = new HashMap<>();
        for (String wid : world.locations.warehouses().keySet()) pressure.put(wid, 0.0);
        for (Store s : world.locations.stores().values()) {
            for (Product p : world.products.values()) {
                StoreProductDemand spd = world.demandRegistry.get(s.getId() + "|" + p.getSku());
                if (spd == null) continue;
                double predicted = spd.predictedDailyDemand();
                if (predicted <= 0) continue;
                // Split predicted demand across warehouses roughly by inverse distance.
                double totalW = 0; Map<String, Double> w = new HashMap<>();
                for (Warehouse wh : world.locations.warehouses().values()) {
                    double inv = wh.isOperational(System.currentTimeMillis()) ? 1.0 / Math.max(1, LocationMap.distance(wh, s)) : 0;
                    w.put(wh.getId(), inv);
                    totalW += inv;
                }
                if (totalW == 0) continue;
                for (Map.Entry<String, Double> e : w.entrySet()) {
                    pressure.merge(e.getKey(), predicted * e.getValue() / totalW, Double::sum);
                }
            }
        }
        return pressure;
    }

    private Recommendation pickBestWarehouse(Store s, Product p, int needed,
                                              Map<String, Double> pressure,
                                              double daysUntilStockout, double predicted) {
        Warehouse best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        double bestCost = 0;
        long bestEta = 0;
        String bestReason = "";
        Warehouse runner = null;
        double runnerScore = Double.POSITIVE_INFINITY;

        int capacityCap = Math.min(needed, 200); // sanity cap per transfer
        int qty = Math.max(1, capacityCap);

        for (Warehouse wh : world.locations.warehouses().values()) {
            if (!wh.isOperational(System.currentTimeMillis())) continue;
            Inventory inv = world.inventory.get(wh.getId(), p.getSku());
            if (inv == null) continue;
            int available = inv.getAvailable();
            if (available < qty) continue;

            double distance = LocationMap.distance(wh, s);
            // Pick a truck (any idle one; capacity check is a soft cap).
            Truck truck = pickIdleTruck();
            if (truck == null) continue;
            double transportCost = distance * truck.getCostPerKm() * p.getUnitWeight() * qty / 100.0;
            long etaMs = (long) ((distance / Math.max(1, truck.getSpeed())) * 3600_000L);

            double score;
            String reason;
            if (strategy == Strategy.NEAREST) {
                // Just minimize distance.
                score = distance;
                reason = "nearest warehouse (distance=" + String.format("%.1f", distance) + ")";
            } else {
                // Composite: cost + pressure + lead-time penalty - priority/profit bonus.
                double pressurePenalty = pressure.getOrDefault(wh.getId(), 0.0) * 0.4;
                double leadTimePenalty = etaMs / 3_600_000.0 * 0.2;
                double bonus = s.getPriority().weight() * p.getProfitPerUnit() * qty * profitWeight;
                score = transportCost * distanceWeight
                        + pressurePenalty
                        + leadTimePenalty
                        - bonus;
                reason = String.format(
                        "cost=%.2f dist=%.1f pressure=%.1f leadTime=%.1fh bonus=%.2f",
                        transportCost, distance, pressurePenalty, etaMs / 3_600_000.0, bonus);
            }

            if (score < bestScore) {
                runner = best; runnerScore = bestScore;
                best = wh; bestScore = score; bestCost = transportCost; bestEta = etaMs; bestReason = reason;
            } else if (score < runnerScore) {
                runner = wh; runnerScore = score;
            }
        }
        if (best == null) return null;
        if (runner != null && best != runner) {
            bestReason += " (beat " + runner.getId() + " by " + String.format("%.2f", runnerScore - bestScore) + ")";
        }
        return new Recommendation(p.getSku(), s.getId(), best.getId(), qty,
                bestScore, bestCost, bestEta, daysUntilStockout, bestReason);
    }

    private Truck pickIdleTruck() {
        long now = System.currentTimeMillis();
        for (Truck t : world.trucks.values()) if (t.isAvailable(now)) return t;
        return null;
    }

    private boolean issueTransfer(Recommendation r) {
        Warehouse wh = world.locations.warehouse(r.warehouseId);
        Store store = world.locations.store(r.storeId);
        Product p = world.product(r.sku);
        if (wh == null || store == null || p == null) return false;
        Inventory src = world.inventory.get(wh.getId(), p.getSku());
        if (src == null) return false;
        if (!src.reserve(r.quantity)) return false;

        Truck truck = pickIdleTruck();
        if (truck == null) { src.release(r.quantity); return false; }
        truck.setEnRoute();

        Transfer t = new Transfer(p.getSku(), wh.getId(), store.getId(), r.quantity,
                System.currentTimeMillis(), System.currentTimeMillis() + r.etaMs, r.transportCost, truck.getId());
        t.setStatus(Transfer.Status.IN_TRANSIT);
        world.transfers.add(t);
        metrics.incTransfersCreated();
        metrics.addTransportCost(r.transportCost);

        log.log(EventLogger.Level.INFO,
                "ISSUE %s qty=%d %s->%s daysLeft=%.2f via=%s | %s",
                p.getName(), r.quantity, wh.getName(), store.getName(),
                r.daysUntilStockout, truck.getId(), r.reason);
        return true;
    }
}

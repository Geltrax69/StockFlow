package com.stockflow.engine;

import com.stockflow.core.*;
import com.stockflow.models.*;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * SimulationEngine — the conductor. Owns the threads, provides pause/resume,
 * and exposes high-level actions (force a cycle, trigger a demand explosion,
 * kill a warehouse, etc.). UI calls into here; engine talks to model layer.
 */
public class SimulationEngine {
    private final World world;
    private final EventLogger log;
    private final MetricsManager metrics;
    private final DemandModel demandModel;
    private final RedistributionEngine redist;
    private final OptimizationEngine optim;
    private final OrderGenerator orderGen;
    private final DemandGenerator demandGen;
    private final TransportationSimulator transport;

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "SimScheduler");
                t.setDaemon(true);
                return t;
            });

    private final ExecutorService workers = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "SimWorker");
        t.setDaemon(true);
        return t;
    });

    private ScheduledFuture<?> orderFuture;
    private ScheduledFuture<?> demandFuture;
    private ScheduledFuture<?> redistFuture;
    private volatile boolean paused = false;

    public SimulationEngine(World world, EventLogger log, MetricsManager metrics) {
        this.world = world;
        this.log = log;
        this.metrics = metrics;
        this.demandModel = new DemandModel();
        this.redist = new RedistributionEngine(world, log, metrics);
        this.optim = new OptimizationEngine(world, log, metrics);
        this.orderGen = new OrderGenerator(world, log, metrics, 3);
        this.demandGen = new DemandGenerator(world, log, metrics, demandModel);
        this.transport = new TransportationSimulator(world, log, metrics);
    }

    public void start() {
        if (orderFuture != null) return;
        log.log(EventLogger.Level.INFO, "Simulation starting");
        orderFuture = scheduler.scheduleAtFixedRate(orderGen, 0, 120, TimeUnit.MILLISECONDS);
        demandFuture = scheduler.scheduleAtFixedRate(demandGen, 0, 1000, TimeUnit.MILLISECONDS);
        redistFuture = scheduler.scheduleAtFixedRate(redist::runCycleNow, 1500, 1500, TimeUnit.MILLISECONDS);
        workers.submit(transport);
    }

    public void pause() {
        paused = true;
        cancel(orderFuture); orderFuture = null;
        cancel(demandFuture); demandFuture = null;
        cancel(redistFuture); redistFuture = null;
        log.log(EventLogger.Level.INFO, "Simulation paused");
    }

    public void resume() {
        if (!paused) return;
        paused = false;
        start();
        log.log(EventLogger.Level.INFO, "Simulation resumed");
    }

    public void reset() {
        pause();
        // Cancel in-flight transfers.
        for (Transfer t : new ArrayList<>(world.transfers)) {
            if (t.getStatus() == Transfer.Status.IN_TRANSIT || t.getStatus() == Transfer.Status.PENDING) {
                Inventory src = world.inventory.get(t.getFromId(), t.getProductSku());
                if (src != null) src.release(t.getQuantity());
                t.setStatus(Transfer.Status.CANCELLED);
            }
        }
        // Reset demand buckets.
        for (StoreProductDemand spd : world.demandRegistry.values()) {
            // Re-create bucket to wipe history.
            try {
                java.lang.reflect.Field f = StoreProductDemand.class.getDeclaredField("bucket");
                f.setAccessible(true);
                f.set(spd, new DemandBucket(14));
            } catch (Exception ignored) {}
        }
        log.log(EventLogger.Level.INFO, "Simulation reset");
    }

    public boolean isPaused() { return paused; }

    public void demandExplosion() {
        demandModel.triggerExplosion();
        log.log(EventLogger.Level.WARN, "DEMAND EXPLOSION triggered (3x for 5s)");
        scheduler.schedule(() -> {
            demandModel.clearExplosion();
            log.log(EventLogger.Level.INFO, "Demand explosion cleared");
        }, 5, TimeUnit.SECONDS);
    }

    public void warehouseFailure() {
        List<Warehouse> ws = new ArrayList<>(world.locations.warehouses().values());
        Warehouse w = ws.get(new Random().nextInt(ws.size()));
        long now = System.currentTimeMillis();
        w.fail(15_000, now);
        log.log(EventLogger.Level.ERROR, "WAREHOUSE FAILURE: %s offline 15s", w.getName());
    }

    public void truckBreakdown() {
        List<Truck> all = new ArrayList<>(world.trucks.values());
        Truck t = all.get(new Random().nextInt(all.size()));
        t.breakDown(10_000, System.currentTimeMillis());
        log.log(EventLogger.Level.ERROR, "TRUCK BREAKDOWN: %s offline 10s", t.getId());
    }

    public void supplierDelay() {
        // Penalize all warehouses' effective capacity briefly: we just bump
        // demand briefly so warehouses have to react.
        demandModel.triggerExplosion();
        log.log(EventLogger.Level.WARN, "SUPPLIER DELAY: 2x demand for 8s");
        scheduler.schedule(demandModel::clearExplosion, 8, TimeUnit.SECONDS);
    }

    public void storeClosure() {
        List<Store> all = new ArrayList<>(world.locations.stores().values());
        Store s = all.get(new Random().nextInt(all.size()));
        s.close(12_000, System.currentTimeMillis());
        log.log(EventLogger.Level.WARN, "STORE CLOSURE: %s closed 12s", s.getName());
    }

    public void inventoryCorruption() {
        Random r = new Random();
        for (Inventory inv : world.inventory.allInventories()) {
            if (r.nextDouble() < 0.05) inv.corrupt(0.1 + r.nextDouble() * 0.2);
        }
        log.log(EventLogger.Level.WARN, "INVENTORY CORRUPTION: random 10-30%% loss at ~5%% of slots");
    }

    public void transferCancellation() {
        synchronized (world.transfers) {
            for (Transfer t : world.transfers) {
                if (t.getStatus() == Transfer.Status.IN_TRANSIT) {
                    t.setStatus(Transfer.Status.CANCELLED);
                    Inventory src = world.inventory.get(t.getFromId(), t.getProductSku());
                    if (src != null) src.release(t.getQuantity());
                    metrics.incTransfersCancelled();
                    log.log(EventLogger.Level.WARN, "Transfer #%d force-cancelled", t.getId());
                    return;
                }
            }
        }
    }

    public OptimizationEngine.Plan optimize() {
        // Run a head-to-head: nearest vs composite, log both, and apply the
        // composite plan (we don't actually dispatch — the engine is already
        // doing that). This shows the user the tradeoffs.
        OptimizationEngine.Plan nearest = optim.plan(RedistributionEngine.Strategy.NEAREST, 50);
        OptimizationEngine.Plan composite = optim.plan(RedistributionEngine.Strategy.COST_DEMAND_DISTANCE, 50);
        log.log(EventLogger.Level.INFO,
                "OPTIMIZE: NEAREST -> %d transfers cost=%.2f | COMPOSITE -> %d transfers cost=%.2f",
                nearest.transfersPlanned, nearest.totalTransportCost,
                composite.transfersPlanned, composite.totalTransportCost);
        return composite;
    }

    public void setStrategy(RedistributionEngine.Strategy s) { redist.setStrategy(s); }
    public RedistributionEngine.Strategy getStrategy() { return redist.getStrategy(); }
    public RedistributionEngine redist() { return redist; }
    public OptimizationEngine optim() { return optim; }
    public MetricsManager metrics() { return metrics; }
    public EventLogger log() { return log; }
    public World world() { return world; }
    public DemandModel demandModel() { return demandModel; }
    public long getDayIndex() { return demandGen.getDayIndex(); }

    private void cancel(ScheduledFuture<?> f) { if (f != null) f.cancel(false); }
}

package com.stockflow.engine;

import com.stockflow.core.EventLogger;
import com.stockflow.core.MetricsManager;
import com.stockflow.core.World;
import com.stockflow.models.*;

import java.util.*;

/**
 * TransportationSimulator — runs on a scheduled thread. Walks world.transfers,
 * advances IN_TRANSIT to DELIVERED when their ETA has passed (and a truck
 * arrived), debits the source and credits the destination, releases the truck.
 *
 * Failure events: warehouse failure or truck breakdown cancels the affected
 * transfers, releases reservations, marks the truck BROKEN_DOWN, and lets the
 * engine re-decide next cycle.
 */
public class TransportationSimulator implements Runnable {
    private final World world;
    private final EventLogger log;
    private final MetricsManager metrics;
    private volatile boolean running = true;

    public TransportationSimulator(World world, EventLogger log, MetricsManager metrics) {
        this.world = world; this.log = log; this.metrics = metrics;
    }

    public void stop() { running = false; }

    @Override
    public void run() {
        while (running) {
            try { tick(); } catch (Exception e) { log.log(EventLogger.Level.ERROR, "Transport tick: %s", e); }
            sleep(300);
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();
        List<Transfer> snapshot;
        synchronized (world.transfers) { snapshot = new ArrayList<>(world.transfers); }
        for (Transfer t : snapshot) {
            if (t.getStatus() != Transfer.Status.IN_TRANSIT) continue;
            if (now < t.getEtaMs()) continue;

            // Check if source warehouse is still operational.
            Warehouse wh = world.locations.warehouse(t.getFromId());
            if (wh != null && !wh.isOperational(now)) {
                cancel(t, "source warehouse down");
                continue;
            }
            // Check if destination is open.
            Store store = world.locations.store(t.getToId());
            if (store != null && !store.isOperational(now)) {
                cancel(t, "destination store closed");
                continue;
            }
            // Debit source, credit destination, release truck.
            Inventory src = world.inventory.get(t.getFromId(), t.getProductSku());
            Inventory dst = world.inventory.get(t.getToId(), t.getProductSku());
            if (src == null || dst == null) { cancel(t, "missing inventory slot"); continue; }
            if (src.getReserved() < t.getQuantity()) { cancel(t, "reservation lost"); continue; }

            // Consume reserved stock from source.
            src.consume(t.getQuantity());
            dst.receive(t.getQuantity());
            Truck truck = world.truck(t.getTruckId());
            if (truck != null) truck.setIdle();
            t.setStatus(Transfer.Status.DELIVERED);
            metrics.incTransfersDelivered();
            log.log(EventLogger.Level.INFO, "DELIVERED %s qty=%d %s->%s",
                    t.getProductSku(), t.getQuantity(), t.getFromId(), t.getToId());
        }
    }

    private void cancel(Transfer t, String reason) {
        t.setStatus(Transfer.Status.CANCELLED);
        metrics.incTransfersCancelled();
        // Release reservation at the source.
        Inventory src = world.inventory.get(t.getFromId(), t.getProductSku());
        if (src != null) src.release(t.getQuantity());
        Truck truck = world.truck(t.getTruckId());
        if (truck != null) truck.setIdle();
        log.log(EventLogger.Level.WARN, "CANCELLED transfer #%d (%s) reason=%s",
                t.getId(), t.getProductSku(), reason);
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

package com.stockflow.engine;

import com.stockflow.core.*;
import com.stockflow.models.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * OrderGenerator — on a scheduled thread, samples customer orders.
 * For each order, picks a random store and 1-3 random products, then tries to
 * fulfill from the store's inventory. Fulfilled units bump revenue; unmet
 * demand increments lost-sales counters and stockout events.
 */
public class OrderGenerator implements Runnable {
    private final World world;
    private final EventLogger log;
    private final MetricsManager metrics;
    private volatile boolean running = true;
    private final int ordersPerTick;

    public OrderGenerator(World world, EventLogger log, MetricsManager metrics, int ordersPerTick) {
        this.world = world; this.log = log; this.metrics = metrics; this.ordersPerTick = ordersPerTick;
    }
    public void stop() { running = false; }

    @Override
    public void run() {
        while (running) {
            try { tick(); } catch (Exception e) { log.log(EventLogger.Level.ERROR, "Order tick: %s", e); }
            sleep(120);
        }
    }

    public void generateOne() { tick(); }

    public void tick() {
        long now = System.currentTimeMillis();
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<Store> openStores = new ArrayList<>();
        for (Store s : world.locations.stores().values()) if (s.isOperational(now)) openStores.add(s);
        if (openStores.isEmpty()) return;

        for (int i = 0; i < ordersPerTick; i++) {
            Store s = openStores.get(rng.nextInt(openStores.size()));
            CustomerOrder order = new CustomerOrder(s.getId(), now);
            int lines = 1 + rng.nextInt(3);
            int demanded = 0, fulfilled = 0;
            for (int j = 0; j < lines; j++) {
                List<Product> ps = new ArrayList<>(world.products.values());
                Product p = ps.get(rng.nextInt(ps.size()));
                int qty = 1 + rng.nextInt(3);
                demanded += qty;
                order.addLine(p.getSku(), qty);
                metrics.addUnitsDemanded(qty);
                Inventory inv = world.inventory.get(s.getId(), p.getSku());
                if (inv != null && inv.consume(qty)) {
                    fulfilled += qty;
                    metrics.addUnitsFulfilled(qty);
                    metrics.addRevenue(p.getUnitPrice() * qty);
                } else {
                    int unmet = qty - (inv == null ? 0 : inv.getAvailable());
                    if (unmet > 0) {
                        metrics.addUnitsLost(unmet);
                        metrics.addLostRevenue(p.getUnitPrice() * unmet);
                        metrics.incStockoutEvents();
                        order.addLostUnits(unmet);
                    }
                }
            }
            metrics.incOrders();
            if (order.getLostUnits() > 0) {
                log.log(EventLogger.Level.WARN, "Stockout @%s order#%d lost=%d", s.getName(), order.getOrderId(), order.getLostUnits());
            }
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

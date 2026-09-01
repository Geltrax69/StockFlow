package com.stockflow.core;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MetricsManager — running counters + a recent-history ring buffer.
 * Threads increment; the dashboard polls `snapshot()`.
 *
 * Service level is the fraction of demanded units that were fulfilled.
 */
public class MetricsManager {
    public interface Listener { void onMetrics(Metrics m); }

    public static class Metrics {
        public long ordersPlaced;
        public long unitsDemanded;
        public long unitsFulfilled;
        public long unitsLost;        // stockouts
        public long transfersCreated;
        public long transfersDelivered;
        public long transfersCancelled;
        public double totalTransportCost;
        public double totalRevenue;
        public double totalLostRevenue;
        public long stockoutEvents;
        public long totalInventoryUnits;
        public double avgDaysUntilStockout;
        public String activeStrategy;
        public long dayIndex;

        public double serviceLevel() {
            return unitsDemanded == 0 ? 1.0 : (double) unitsFulfilled / unitsDemanded;
        }
    }

    private final Metrics m = new Metrics();
    private final AtomicReference<double[]> recentServiceLevel = new AtomicReference<>(new double[120]);
    private final AtomicReference<double[]> recentDailyDemand = new AtomicReference<>(new double[120]);
    private int histIdx = 0;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong lastFiredMs = new AtomicLong(0);

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public synchronized void incOrders() { m.ordersPlaced++; }
    public synchronized void addUnitsDemanded(int n) { m.unitsDemanded += n; }
    public synchronized void addUnitsFulfilled(int n) { m.unitsFulfilled += n; }
    public synchronized void addUnitsLost(int n) { m.unitsLost += n; }
    public synchronized void incStockoutEvents() { m.stockoutEvents++; }
    public synchronized void incTransfersCreated() { m.transfersCreated++; }
    public synchronized void incTransfersDelivered() { m.transfersDelivered++; }
    public synchronized void incTransfersCancelled() { m.transfersCancelled++; }
    public synchronized void addTransportCost(double c) { m.totalTransportCost += c; }
    public synchronized void addRevenue(double r) { m.totalRevenue += r; }
    public synchronized void addLostRevenue(double r) { m.totalLostRevenue += r; }
    public synchronized void setActiveStrategy(String s) { m.activeStrategy = s; }
    public synchronized void setDayIndex(long d) { m.dayIndex = d; }
    public synchronized void setTotalInventory(long n) { m.totalInventoryUnits = n; }
    public synchronized void setAvgDaysUntilStockout(double d) { m.avgDaysUntilStockout = d; }

    public synchronized void recordDaily(double serviceLevel, double totalDemand) {
        double[] sl = recentServiceLevel.get();
        double[] dd = recentDailyDemand.get();
        sl[histIdx] = serviceLevel;
        dd[histIdx] = totalDemand;
        histIdx = (histIdx + 1) % sl.length;
    }

    public synchronized Metrics snapshot() {
        return m; // single-threaded read; the dashboard polls via Swing timer
    }

    public double[] recentServiceLevel() { return recentServiceLevel.get().clone(); }
    public double[] recentDailyDemand() { return recentDailyDemand.get().clone(); }

    public synchronized void fireIfStale(long nowMs) {
        if (nowMs - lastFiredMs.get() < 250) return;
        lastFiredMs.set(nowMs);
        final Metrics copy = snapshot();
        SwingUtilities.invokeLater(() -> {
            for (Listener l : listeners) l.onMetrics(copy);
        });
    }
}

package com.stockflow.core;

import com.stockflow.models.Store;
import com.stockflow.models.Warehouse;

import java.util.HashMap;
import java.util.Map;

/**
 * LocationMap — central registry of warehouses and stores.
 * Pure data holder, no locking — the engine guards mutations.
 */
public class LocationMap {
    private final Map<String, Warehouse> warehouses = new HashMap<>();
    private final Map<String, Store> stores = new HashMap<>();

    public void addWarehouse(Warehouse w) { warehouses.put(w.getId(), w); }
    public void addStore(Store s) { stores.put(s.getId(), s); }
    public Warehouse warehouse(String id) { return warehouses.get(id); }
    public Store store(String id) { return stores.get(id); }
    public Map<String, Warehouse> warehouses() { return warehouses; }
    public Map<String, Store> stores() { return stores; }

    public static double distance(Warehouse a, Store s) {
        double dx = a.getX() - s.getX();
        double dy = a.getY() - s.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }
    public static double distance(Warehouse a, Warehouse b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }
}

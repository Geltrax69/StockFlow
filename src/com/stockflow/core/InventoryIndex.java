package com.stockflow.core;

import com.stockflow.models.Inventory;

import java.util.HashMap;
import java.util.Map;

/**
 * InventoryIndex — fast lookup by (locationId, sku).
 * HashMap of HashMaps; the engine's locks guard writes. Fine at 5-10 warehouses
 * × 50 stores × 100 products; for millions switch to a trie or a
 * LongMap<LongMap<Inventory>>.
 */
public class InventoryIndex {
    private final Map<String, Map<String, Inventory>> byLocation = new HashMap<>();

    public void put(Inventory inv) {
        byLocation.computeIfAbsent(inv.getLocationId(), k -> new HashMap<>())
                  .put(inv.getProductSku(), inv);
    }

    public Inventory get(String locationId, String sku) {
        Map<String, Inventory> m = byLocation.get(locationId);
        return m == null ? null : m.get(sku);
    }

    public Map<String, Inventory> at(String locationId) {
        return byLocation.getOrDefault(locationId, new HashMap<>());
    }

    public Iterable<Map<String, Inventory>> all() { return byLocation.values(); }

    /** Snapshot of every inventory — for the metrics panel. */
    public java.util.List<Inventory> allInventories() {
        java.util.List<Inventory> out = new java.util.ArrayList<>();
        for (Map<String, Inventory> m : byLocation.values()) out.addAll(m.values());
        return out;
    }
}

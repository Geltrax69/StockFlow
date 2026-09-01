package com.stockflow.core;

import com.stockflow.models.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World — the simulation's shared state container. Single instance.
 * Concurrency model:
 *   - Inventory has its own monitor (synchronized methods).
 *   - Maps here are ConcurrentHashMap; we use computeIfAbsent for atomics.
 *   - The engine takes a coarse read/write lock on the world only when
 *     re-shaping topology (rare), not on the hot path.
 */
public class World {
    public final LocationMap locations = new LocationMap();
    public final InventoryIndex inventory = new InventoryIndex();
    public final Map<String, Product> products = new ConcurrentHashMap<>();
    public final Map<String, Truck> trucks = new ConcurrentHashMap<>();
    public final Map<String, StoreProductDemand> demandRegistry = new ConcurrentHashMap<>();
    public final List<Transfer> transfers = Collections.synchronizedList(new ArrayList<>());

    public Product product(String sku) { return products.get(sku); }
    public Truck truck(String id) { return trucks.get(id); }
    public List<Transfer> transfers() { return transfers; }
}

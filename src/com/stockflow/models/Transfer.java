package com.stockflow.models;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Transfer — movement of stock from a warehouse to a store (or store-to-store
 * in advanced mode). Lifecycle: PENDING -> IN_TRANSIT -> DELIVERED, or CANCELLED.
 */
public class Transfer implements Serializable, Comparable<Transfer> {
    private static final long serialVersionUID = 1L;
    private static final AtomicLong ID_GEN = new AtomicLong(1);

    public enum Status { PENDING, IN_TRANSIT, DELIVERED, CANCELLED }

    private final long id;
    private final String productSku;
    private final String fromId;
    private final String toId;
    private final int quantity;
    private final long createdAtMs;
    private final long etaMs;
    private final double cost;
    private final String truckId;
    private volatile Status status = Status.PENDING;

    public Transfer(String productSku, String fromId, String toId, int quantity,
                    long createdAtMs, long etaMs, double cost, String truckId) {
        this.id = ID_GEN.getAndIncrement();
        this.productSku = productSku;
        this.fromId = fromId;
        this.toId = toId;
        this.quantity = quantity;
        this.createdAtMs = createdAtMs;
        this.etaMs = etaMs;
        this.cost = cost;
        this.truckId = truckId;
    }

    public long getId() { return id; }
    public String getProductSku() { return productSku; }
    public String getFromId() { return fromId; }
    public String getToId() { return toId; }
    public int getQuantity() { return quantity; }
    public long getCreatedAtMs() { return createdAtMs; }
    public long getEtaMs() { return etaMs; }
    public double getCost() { return cost; }
    public String getTruckId() { return truckId; }
    public Status getStatus() { return status; }
    public void setStatus(Status s) { this.status = s; }

    @Override
    public int compareTo(Transfer o) { return Long.compare(this.id, o.id); }
    @Override
    public String toString() {
        return "Transfer#" + id + " " + productSku + " " + quantity + " " + fromId + "->" + toId +
               " cost=" + String.format("%.2f", cost) + " eta=" + etaMs + "ms " + status;
    }
}

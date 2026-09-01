package com.stockflow.models;

import java.io.Serializable;

/**
 * Product — the smallest unit in the supply chain.
 * Holds SKU, name, category, base price, weight, and reorder parameters.
 */
public class Product implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String sku;
    private final String name;
    private final String category;
    private final double unitPrice;
    private final double unitWeight;
    private final double unitCost;
    private final double profitMargin; // (price - cost) / cost
    private final int leadTimeDays;
    private final int shelfLifeDays;

    public Product(String sku, String name, String category, double unitPrice,
                   double unitCost, double unitWeight, int leadTimeDays, int shelfLifeDays) {
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.unitPrice = unitPrice;
        this.unitCost = unitCost;
        this.unitWeight = unitWeight;
        this.profitMargin = (unitCost > 0) ? (unitPrice - unitCost) / unitCost : 0;
        this.leadTimeDays = leadTimeDays;
        this.shelfLifeDays = shelfLifeDays;
    }

    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getUnitPrice() { return unitPrice; }
    public double getUnitCost() { return unitCost; }
    public double getUnitWeight() { return unitWeight; }
    public double getProfitMargin() { return profitMargin; }
    public double getProfitPerUnit() { return unitPrice - unitCost; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public int getShelfLifeDays() { return shelfLifeDays; }

    @Override
    public String toString() {
        return name + " (" + sku + ")";
    }
}

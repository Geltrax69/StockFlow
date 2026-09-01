package com.stockflow.ui;

import com.stockflow.core.World;
import com.stockflow.core.StoreProductDemand;
import com.stockflow.models.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.List;

/**
 * InventoryTable — shows inventory for the currently selected location.
 * Updates cheaply because we mutate model rows in-place.
 */
public class InventoryTable extends JPanel {
    private final World world;
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Product", "Stock", "Reserved", "Available", "Avg Daily Demand", "Days Left"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private Object currentLocation; // Warehouse or Store
    private final List<String> sortKeys = new ArrayList<>();

    public InventoryTable(World world) {
        this.world = world;
        setLayout(new BorderLayout());
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createTitledBorder("Inventory"));
        add(sp, BorderLayout.CENTER);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
    }

    public void setLocation(Object loc) {
        this.currentLocation = loc;
        refresh();
    }

    public void refresh() {
        if (currentLocation == null) return;
        String locId;
        if (currentLocation instanceof Warehouse) locId = ((Warehouse) currentLocation).getId();
        else if (currentLocation instanceof Store) locId = ((Store) currentLocation).getId();
        else return;

        Map<String, Inventory> invs = world.inventory.at(locId);
        // Sort by sku to keep rows stable.
        List<String> keys = new ArrayList<>(invs.keySet());
        Collections.sort(keys);
        // Detect size change cheaply.
        if (model.getRowCount() != keys.size()) {
            model.setRowCount(0);
            for (String sku : keys) {
                Inventory inv = invs.get(sku);
                Product p = world.product(sku);
                double predicted = 0;
                if (currentLocation instanceof Store) {
                    StoreProductDemand spd = world.demandRegistry.get(locId + "|" + sku);
                    if (spd != null) predicted = spd.predictedDailyDemand();
                }
                double daysLeft = predicted > 0 ? (double) inv.getAvailable() / predicted : 999;
                model.addRow(new Object[]{
                        p == null ? sku : p.getName(),
                        inv.getStock(),
                        inv.getReserved(),
                        inv.getAvailable(),
                        String.format("%.1f", predicted),
                        daysLeft >= 100 ? "∞" : String.format("%.1f", daysLeft)
                });
            }
        } else {
            for (int i = 0; i < keys.size(); i++) {
                String sku = keys.get(i);
                Inventory inv = invs.get(sku);
                double predicted = 0;
                if (currentLocation instanceof Store) {
                    StoreProductDemand spd = world.demandRegistry.get(locId + "|" + sku);
                    if (spd != null) predicted = spd.predictedDailyDemand();
                }
                double daysLeft = predicted > 0 ? (double) inv.getAvailable() / predicted : 999;
                model.setValueAt(inv.getStock(), i, 1);
                model.setValueAt(inv.getReserved(), i, 2);
                model.setValueAt(inv.getAvailable(), i, 3);
                model.setValueAt(String.format("%.1f", predicted), i, 4);
                model.setValueAt(daysLeft >= 100 ? "∞" : String.format("%.1f", daysLeft), i, 5);
            }
        }
    }
}

package com.stockflow.ui;

import com.stockflow.core.World;
import com.stockflow.models.Transfer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * TransferTable — shows the most recent transfers and their status.
 */
public class TransferTable extends JPanel {
    private final World world;
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"#", "Product", "From", "To", "Qty", "Cost", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private long lastSeenId = 0;

    public TransferTable(World world) {
        this.world = world;
        setLayout(new BorderLayout());
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createTitledBorder("Transfers"));
        add(sp, BorderLayout.CENTER);
        table.setFillsViewportHeight(true);
        table.setRowHeight(20);
    }

    public void refresh() {
        List<Transfer> all;
        synchronized (world.transfers) { all = List.copyOf(world.transfers); }
        // Find newest id first.
        long max = lastSeenId;
        for (Transfer t : all) if (t.getId() > max) max = t.getId();
        // We cap to last 60 rows.
        int target = Math.min(60, all.size());
        if (model.getRowCount() != target) {
            model.setRowCount(0);
            int start = Math.max(0, all.size() - target);
            for (int i = start; i < all.size(); i++) addRow(all.get(i));
        } else {
            for (int i = 0; i < target; i++) {
                Transfer t = all.get(all.size() - target + i);
                model.setValueAt(t.getId(), i, 0);
                model.setValueAt(t.getProductSku(), i, 1);
                model.setValueAt(t.getFromId(), i, 2);
                model.setValueAt(t.getToId(), i, 3);
                model.setValueAt(t.getQuantity(), i, 4);
                model.setValueAt(String.format("%.2f", t.getCost()), i, 5);
                model.setValueAt(t.getStatus(), i, 6);
            }
        }
        lastSeenId = max;
    }

    private void addRow(Transfer t) {
        model.addRow(new Object[]{
                t.getId(), t.getProductSku(), t.getFromId(), t.getToId(),
                t.getQuantity(), String.format("%.2f", t.getCost()), t.getStatus()
        });
    }
}

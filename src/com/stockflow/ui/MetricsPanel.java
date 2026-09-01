package com.stockflow.ui;

import com.stockflow.core.MetricsManager;
import com.stockflow.engine.RedistributionEngine;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * MetricsPanel — shows running counters in a small grid. The dashboard calls
 * refresh() from a Swing timer.
 */
public class MetricsPanel extends JPanel {
    private final JLabel orders = new JLabel("0");
    private final JLabel fulfilled = new JLabel("0");
    private final JLabel lost = new JLabel("0");
    private final JLabel stockouts = new JLabel("0");
    private final JLabel cost = new JLabel("0.00");
    private final JLabel revenue = new JLabel("0.00");
    private final JLabel lostRev = new JLabel("0.00");
    private final JLabel transfers = new JLabel("0");
    private final JLabel inventory = new JLabel("0");
    private final JLabel avgDays = new JLabel("0.0");
    private final JLabel serviceLevel = new JLabel("100.0%");
    private final JLabel strategy = new JLabel("—");
    private final JLabel day = new JLabel("0");

    public MetricsPanel() {
        setLayout(new GridLayout(0, 4, 8, 8));
        setBorder(BorderFactory.createTitledBorder("Metrics"));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        add(row("Day", day));
        add(row("Strategy", strategy));
        add(row("Service Level", serviceLevel));
        add(row("Avg Days Left", avgDays));
        add(row("Orders", orders));
        add(row("Units Fulfilled", fulfilled));
        add(row("Units Lost", lost));
        add(row("Stockouts", stockouts));
        add(row("Transfers", transfers));
        add(row("Total Stock", inventory));
        add(row("Transport Cost", cost));
        add(row("Revenue", revenue));
        add(row("Lost Revenue", lostRev));
        add(row("", new JLabel("")));
    }

    private JPanel row(String title, JLabel value) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(4, 8, 4, 8)));
        JLabel t = new JLabel(title);
        t.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        t.setForeground(new Color(100, 100, 100));
        value.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        p.add(t, BorderLayout.NORTH);
        p.add(value, BorderLayout.CENTER);
        return p;
    }

    public void refresh(MetricsManager.Metrics m, double[] sl, double[] dd, RedistributionEngine.Strategy s, long dayIdx) {
        day.setText(String.valueOf(dayIdx));
        strategy.setText(s.name());
        serviceLevel.setText(String.format("%.1f%%", m.serviceLevel() * 100));
        avgDays.setText(String.format("%.1f", m.avgDaysUntilStockout));
        orders.setText(String.valueOf(m.ordersPlaced));
        fulfilled.setText(String.valueOf(m.unitsFulfilled));
        lost.setText(String.valueOf(m.unitsLost));
        stockouts.setText(String.valueOf(m.stockoutEvents));
        transfers.setText(m.transfersCreated + " (" + m.transfersDelivered + " ok, " + m.transfersCancelled + " no)");
        cost.setText(String.format("%.2f", m.totalTransportCost));
        revenue.setText(String.format("%.2f", m.totalRevenue));
        lostRev.setText(String.format("%.2f", m.totalLostRevenue));
        inventory.setText(String.valueOf(m.totalInventoryUnits));
    }
}

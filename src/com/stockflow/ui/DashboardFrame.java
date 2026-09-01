package com.stockflow.ui;

import com.stockflow.core.*;
import com.stockflow.engine.OptimizationEngine;
import com.stockflow.engine.RedistributionEngine;
import com.stockflow.engine.SimulationEngine;
import com.stockflow.models.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * DashboardFrame — the top-level Swing window. Builds the layout, wires the
 * buttons, and pumps the Swing repaint timer.
 */
public class DashboardFrame extends JFrame {
    private final World world;
    private final EventLogger logger;
    private final MetricsManager metrics;
    private final SimulationEngine sim;

    private final MapPanel mapPanel;
    private final InventoryTable inventoryTable = new InventoryTable(/*world*/null);
    private final TransferTable transferTable = new TransferTable(null);
    private final MetricsPanel metricsPanel = new MetricsPanel();
    private final EventLogPanel eventLogPanel = new EventLogPanel();
    private final LineChart chart = new LineChart();

    private final JLabel status = new JLabel("Idle");
    private final JComboBox<String> strategyBox = new JComboBox<>(new String[]{
            RedistributionEngine.Strategy.NEAREST.name(),
            RedistributionEngine.Strategy.COST_DEMAND_DISTANCE.name()});

    private final ScheduledExecutorService swing = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SwingPulse"); t.setDaemon(true); return t;
    });

    public DashboardFrame() {
        this.logger = new EventLogger();
        this.metrics = new MetricsManager();
        this.world = bootstrapWorld();
        this.sim = new SimulationEngine(world, logger, metrics);
        this.mapPanel = new MapPanel(world);

        // Init tables with world.
        try {
            java.lang.reflect.Field f = InventoryTable.class.getDeclaredField("world");
            f.setAccessible(true); f.set(this.inventoryTable, world);
            java.lang.reflect.Field f2 = TransferTable.class.getDeclaredField("world");
            f2.setAccessible(true); f2.set(this.transferTable, world);
        } catch (Exception e) { throw new RuntimeException(e); }

        setTitle("StockFlow — Intelligent Inventory Redistribution");
        setSize(1400, 880);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        logger.addListener(eventLogPanel);

        mapPanel.addPropertyChangeListener("locationSelected", evt -> {
            Object o = evt.getNewValue();
            if (o != null) inventoryTable.setLocation(o);
        });

        // Auto-select first warehouse.
        Warehouse first = world.locations.warehouses().values().iterator().next();
        inventoryTable.setLocation(first);

        // 4 Hz repaint.
        swing.scheduleAtFixedRate(this::pulse, 0, 250, TimeUnit.MILLISECONDS);
    }

    private JComponent buildToolbar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        p.setBackground(new Color(33, 37, 41));
        p.setBorder(new EmptyBorder(4, 8, 4, 8));
        p.add(btn("Start", e -> sim.start()));
        p.add(btn("Pause", e -> sim.pause()));
        p.add(btn("Resume", e -> sim.resume()));
        p.add(btn("Reset", e -> sim.reset()));
        p.add(btn("Generate Order", e -> sim.redist().runCycle()));
        p.add(btn("Demand Spike", e -> sim.demandExplosion()));
        p.add(btn("Warehouse Failure", e -> sim.warehouseFailure()));
        p.add(btn("Truck Failure", e -> sim.truckBreakdown()));
        p.add(btn("Supplier Delay", e -> sim.supplierDelay()));
        p.add(btn("Store Closure", e -> sim.storeClosure()));
        p.add(btn("Inventory Corruption", e -> sim.inventoryCorruption()));
        p.add(btn("Cancel Transfer", e -> sim.transferCancellation()));
        p.add(btn("Optimize Inventory", e -> runOptimize()));
        p.add(new JLabel(" Strategy:"));
        strategyBox.setSelectedItem(sim.getStrategy().name());
        strategyBox.addActionListener(e -> {
            RedistributionEngine.Strategy s = RedistributionEngine.Strategy.valueOf((String) strategyBox.getSelectedItem());
            sim.setStrategy(s);
        });
        p.add(strategyBox);
        return p;
    }

    private JButton btn(String text, java.awt.event.ActionListener l) {
        JButton b = new JButton(text);
        b.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        b.setMargin(new Insets(4, 8, 4, 8));
        b.setBackground(new Color(52, 58, 64));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        b.addActionListener(l);
        return b;
    }

    private JComponent buildCenter() {
        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.setBorder(new EmptyBorder(6, 6, 6, 6));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mapPanel, buildRight());
        split.setResizeWeight(0.6);
        center.add(split, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        bottom.add(metricsPanel, BorderLayout.NORTH);
        JPanel tables = new JPanel(new GridLayout(1, 2, 6, 6));
        tables.add(inventoryTable);
        tables.add(transferTable);
        bottom.add(tables, BorderLayout.CENTER);
        bottom.add(chart, BorderLayout.SOUTH);
        center.add(bottom, BorderLayout.SOUTH);

        return center;
    }

    private JComponent buildRight() {
        JPanel right = new JPanel(new BorderLayout(6, 6));
        right.add(eventLogPanel, BorderLayout.CENTER);
        return right;
    }

    private JComponent buildStatusBar() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(2, 8, 2, 8));
        p.add(status, BorderLayout.WEST);
        return p;
    }

    private void runOptimize() {
        OptimizationEngine.Plan p = sim.optimize();
        status.setText("Optimized (" + p.strategy + "): " + p.transfersPlanned + " transfers, est cost=" +
                String.format("%.2f", p.totalTransportCost));
    }

    private void pulse() {
        SwingUtilities.invokeLater(() -> {
            try {
                mapPanel.repaint();
                inventoryTable.refresh();
                transferTable.refresh();
                metricsPanel.refresh(metrics.snapshot(), metrics.recentServiceLevel(),
                        metrics.recentDailyDemand(), sim.getStrategy(), sim.getDayIndex());
                chart.setData(metrics.recentServiceLevel(), metrics.recentDailyDemand());
                metrics.fireIfStale(System.currentTimeMillis());
                status.setText(sim.isPaused() ? "Paused" : "Running  |  Day " + sim.getDayIndex());
            } catch (Exception ignored) {}
        });
    }

    private World bootstrapWorld() {
        World w = new World();

        // Warehouses: 8 around the perimeter.
        String[] whNames = {"AlphaHub", "BetaDepot", "GammaCentral", "DeltaStore",
                "EpsilonYard", "ZetaBase", "EtaTerminal", "ThetaPort"};
        double[][] whCoords = {
                {120, 120}, {880, 110}, {120, 560}, {880, 560},
                {500, 80}, {500, 600}, {200, 350}, {800, 350}
        };
        for (int i = 0; i < whNames.length; i++) {
            Warehouse wh = new Warehouse("W" + (i + 1), whNames[i], whCoords[i][0], whCoords[i][1], 800);
            w.locations.addWarehouse(wh);
        }

        // Stores: 30 placed randomly inside the map.
        String[] storeNames = new String[30];
        Random r = new Random(42);
        Store.Priority[] prios = Store.Priority.values();
        for (int i = 0; i < 30; i++) {
            storeNames[i] = "Store-" + String.format("%02d", i + 1);
            double x = 100 + r.nextDouble() * 800;
            double y = 100 + r.nextDouble() * 500;
            Store.Priority p = prios[r.nextInt(prios.length)];
            Store s = new Store("S" + (i + 1), storeNames[i], x, y, p);
            w.locations.addStore(s);
        }

        // Products: 50 across 6 categories.
        String[] categories = {"Apparel", "Electronics", "Grocery", "Footwear", "Beauty", "Home"};
        String[][] itemsPerCat = {
                {"Shirt", "Jeans", "Jacket", "Cap", "Belt", "Scarf", "Sweater", "TShirt"},
                {"Earbuds", "Charger", "Mouse", "Keyboard", "USB", "Cable", "Adapter", "Battery"},
                {"Rice", "Pasta", "Cereal", "Soap", "Oil", "Sugar", "Tea", "Coffee"},
                {"Shoes", "Sneakers", "Sandals", "Boots", "Slippers", "Loafers", "Heels", "Wedges"},
                {"Lipstick", "Lotion", "Perfume", "Shampoo", "Cream", "Mask", "Serum", "Toner"},
                {"Lamp", "Pillow", "Blanket", "Mug", "Vase", "Clock", "Frame", "Mat"}
        };
        int productId = 1;
        for (int c = 0; c < categories.length; c++) {
            for (int j = 0; j < itemsPerCat[c].length; j++) {
                String name = itemsPerCat[c][j];
                String sku = "P" + String.format("%03d", productId++);
                double price = 5 + r.nextDouble() * 195;
                double cost = price * (0.4 + r.nextDouble() * 0.3);
                double weight = 0.1 + r.nextDouble() * 4.0;
                int lead = 1 + r.nextInt(7);
                Product p = new Product(sku, name, categories[c], price, cost, weight, lead, 365);
                w.products.put(sku, p);
            }
        }

        // Initial inventory: warehouses hold bulk, stores hold a few days.
        for (Warehouse wh : w.locations.warehouses().values()) {
            for (Product p : w.products.values()) {
                int stock = 200 + r.nextInt(800);
                Inventory inv = new Inventory(wh.getId(), p.getSku(), stock);
                w.inventory.put(inv);
            }
        }
        for (Store s : w.locations.stores().values()) {
            for (Product p : w.products.values()) {
                int stock = 10 + r.nextInt(40);
                Inventory inv = new Inventory(s.getId(), p.getSku(), stock);
                w.inventory.put(inv);
                StoreProductDemand spd = new StoreProductDemand(s, p);
                w.demandRegistry.put(s.getId() + "|" + p.getSku(), spd);
            }
        }

        // Trucks: 12.
        for (int i = 0; i < 12; i++) {
            Truck t = new Truck(500, 60, 0.5);
            w.trucks.put(t.getId(), t);
        }

        logger.log(EventLogger.Level.INFO, "World bootstrapped: %d warehouses, %d stores, %d products, %d trucks",
                w.locations.warehouses().size(), w.locations.stores().size(),
                w.products.size(), w.trucks.size());
        return w;
    }
}

package com.stockflow.ui;

import com.stockflow.core.InventoryIndex;
import com.stockflow.core.LocationMap;
import com.stockflow.core.World;
import com.stockflow.models.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * MapPanel — a custom-painted Swing view of the world. Renders:
 *   - Background grid for orientation.
 *   - Warehouses as squares, stores as circles, with size proportional to stock.
 *   - Live transfer arrows (blue = in transit, green = delivered, red = cancelled).
 *   - Hover tooltip showing detailed stats.
 *
 * No external libraries; pure AWT paint + Swing component.
 */
public class MapPanel extends JPanel {
    private final World world;
    private String selectedLocationId = null;
    private String selectedProductSku = null;

    public MapPanel(World world) {
        this.world = world;
        setBackground(new Color(245, 247, 250));
        setPreferredSize(new Dimension(900, 500));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { handleClick(e); }
        });
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    public void setSelection(String locationId, String productSku) {
        this.selectedLocationId = locationId;
        this.selectedProductSku = productSku;
        repaint();
    }

    private void handleClick(MouseEvent e) {
        Point p = e.getPoint();
        // Hit-test stores first (smaller), then warehouses.
        for (Store s : world.locations.stores().values()) {
            Point sp = toPixel(s.getX(), s.getY());
            if (sp.distance(p) < 18) {
                setSelection(s.getId(), null);
                firePropertyChange("locationSelected", null, s);
                return;
            }
        }
        for (Warehouse w : world.locations.warehouses().values()) {
            Point wp = toPixel(w.getX(), w.getY());
            if (wp.distance(p) < 22) {
                setSelection(w.getId(), null);
                firePropertyChange("locationSelected", null, w);
                return;
            }
        }
    }

    private Point toPixel(double x, double y) {
        int w = getWidth(), h = getHeight();
        int px = (int) (40 + (x / 1000.0) * (w - 80));
        int py = (int) (40 + (y / 700.0) * (h - 80));
        return new Point(px, py);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // grid
        g2.setColor(new Color(220, 224, 230));
        for (int x = 0; x < getWidth(); x += 40) g2.drawLine(x, 0, x, getHeight());
        for (int y = 0; y < getHeight(); y += 40) g2.drawLine(0, y, getWidth(), y);

        // transfers
        paintTransfers(g2);

        // warehouses
        for (Warehouse w : world.locations.warehouses().values()) paintWarehouse(g2, w);

        // stores
        for (Store s : world.locations.stores().values()) paintStore(g2, s);

        g2.dispose();
    }

    private void paintTransfers(Graphics2D g2) {
        synchronized (world.transfers) {
            for (Transfer t : world.transfers) {
                Warehouse wh = world.locations.warehouse(t.getFromId());
                Store st = world.locations.store(t.getToId());
                if (wh == null || st == null) continue;
                Point a = toPixel(wh.getX(), wh.getY());
                Point b = toPixel(st.getX(), st.getY());
                Color c;
                switch (t.getStatus()) {
                    case IN_TRANSIT: c = new Color(33, 150, 243, 200); break;
                    case DELIVERED: c = new Color(76, 175, 80, 120); break;
                    case CANCELLED: c = new Color(244, 67, 54, 120); break;
                    default: c = Color.GRAY;
                }
                g2.setColor(c);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(a.x, a.y, b.x, b.y);
            }
        }
    }

    private void paintWarehouse(Graphics2D g2, Warehouse w) {
        Point p = toPixel(w.getX(), w.getY());
        int total = 0;
        for (Inventory inv : world.inventory.at(w.getId()).values()) total += inv.getStock();
        int size = 18 + Math.min(40, total / 500);
        boolean selected = w.getId().equals(selectedLocationId);
        g2.setColor(w.isOperational(System.currentTimeMillis()) ? new Color(255, 152, 0) : new Color(150, 150, 150));
        g2.fillRect(p.x - size / 2, p.y - size / 2, size, size);
        if (selected) {
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(3f));
            g2.drawRect(p.x - size / 2 - 3, p.y - size / 2 - 3, size + 6, size + 6);
        }
        g2.setColor(Color.DARK_GRAY);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g2.drawString(w.getName(), p.x - size / 2, p.y - size / 2 - 4);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g2.drawString("stock: " + total, p.x - size / 2, p.y + size / 2 + 14);
    }

    private void paintStore(Graphics2D g2, Store s) {
        Point p = toPixel(s.getX(), s.getY());
        int total = 0;
        for (Inventory inv : world.inventory.at(s.getId()).values()) total += inv.getStock();
        int size = 12 + Math.min(20, total / 100);
        boolean selected = s.getId().equals(selectedLocationId);
        Color base = switch (s.getPriority()) {
            case GOLD -> new Color(255, 193, 7);
            case SILVER -> new Color(158, 158, 158);
            case BRONZE -> new Color(121, 85, 72);
        };
        g2.setColor(s.isOperational(System.currentTimeMillis()) ? base : new Color(180, 180, 180));
        g2.fillOval(p.x - size / 2, p.y - size / 2, size, size);
        if (selected) {
            g2.setColor(Color.BLACK);
            g2.setStroke(new BasicStroke(2.5f));
            g2.drawOval(p.x - size / 2 - 3, p.y - size / 2 - 3, size + 6, size + 6);
        }
        g2.setColor(Color.DARK_GRAY);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g2.drawString(s.getName() + " (" + total + ")", p.x - size / 2, p.y + size / 2 + 14);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        for (Warehouse w : world.locations.warehouses().values()) {
            Point p = toPixel(w.getX(), w.getY());
            if (p.distance(e.getPoint()) < 22) {
                int total = 0;
                for (Inventory inv : world.inventory.at(w.getId()).values()) total += inv.getStock();
                return "<html><b>" + w.getName() + "</b> (warehouse)<br/>Stock: " + total +
                        "<br/>Operational: " + w.isOperational(System.currentTimeMillis()) + "</html>";
            }
        }
        for (Store s : world.locations.stores().values()) {
            Point p = toPixel(s.getX(), s.getY());
            if (p.distance(e.getPoint()) < 18) {
                int total = 0;
                for (Inventory inv : world.inventory.at(s.getId()).values()) total += inv.getStock();
                return "<html><b>" + s.getName() + "</b> (" + s.getPriority() + ")<br/>Stock: " + total +
                        "<br/>Operational: " + s.isOperational(System.currentTimeMillis()) + "</html>";
            }
        }
        return null;
    }
}

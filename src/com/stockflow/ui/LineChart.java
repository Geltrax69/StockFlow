package com.stockflow.ui;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * LineChart — minimal but pretty line-chart panel used for the demand graph.
 * Pure AWT, no third-party libraries.
 */
public class LineChart extends JPanel {
    private double[] seriesA;
    private double[] seriesB;
    private double maxA, maxB;
    private final Color colorA = new Color(33, 150, 243);
    private final Color colorB = new Color(244, 67, 54);
    private String labelA = "Service Level";
    private String labelB = "Demand";
    public LineChart() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(420, 160));
    }

    public void setData(double[] a, double[] b) {
        this.seriesA = a;
        this.seriesB = b;
        this.maxA = a == null ? 0 : Arrays.stream(a).max().orElse(1);
        this.maxB = b == null ? 0 : Arrays.stream(b).max().orElse(1);
        if (maxA <= 0) maxA = 1;
        if (maxB <= 0) maxB = 1;
        repaint();
    }
    public void setLabels(String a, String b) { this.labelA = a; this.labelB = b; repaint(); }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        g2.setColor(new Color(238, 238, 238));
        g2.drawLine(40, 10, 40, h - 30);
        g2.drawLine(40, h - 30, w - 10, h - 30);

        if (seriesA != null && seriesA.length > 1) drawSeries(g2, seriesA, colorA, maxA, 1.0);
        if (seriesB != null && seriesB.length > 1) drawSeries(g2, seriesB, colorB, maxB, 0.6);

        // legend
        g2.setColor(colorA); g2.fillRect(50, 8, 10, 10);
        g2.setColor(Color.DARK_GRAY); g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g2.drawString(labelA, 64, 17);
        g2.setColor(colorB); g2.fillRect(170, 8, 10, 10);
        g2.setColor(Color.DARK_GRAY);
        g2.drawString(labelB, 184, 17);

        g2.dispose();
    }

    private void drawSeries(Graphics2D g2, double[] data, Color c, double max, double alpha) {
        int w = getWidth(), h = getHeight();
        int n = data.length;
        int left = 40, right = w - 10, top = 10, bottom = h - 30;
        g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), (int)(alpha * 255)));
        g2.setStroke(new BasicStroke(2f));
        int prevX = -1, prevY = -1;
        for (int i = 0; i < n; i++) {
            double v = data[i];
            int x = left + (int) ((right - left) * ((double) i / (n - 1)));
            int y = bottom - (int) ((bottom - top) * (v / max));
            if (prevX >= 0) g2.drawLine(prevX, prevY, x, y);
            prevX = x; prevY = y;
        }
    }
}

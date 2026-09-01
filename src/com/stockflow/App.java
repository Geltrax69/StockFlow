package com.stockflow;

import com.stockflow.ui.DashboardFrame;

import javax.swing.*;

/**
 * App — entry point. Boots the EDT, shows the dashboard.
 */
public class App {
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new DashboardFrame().setVisible(true));
    }
}

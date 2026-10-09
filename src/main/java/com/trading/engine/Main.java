package com.trading.engine;

import com.trading.engine.ui.DashboardFrame;

import javax.swing.SwingUtilities;

/** Launches the desktop dashboard for the order matching engine. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DashboardFrame().setVisible(true));
    }
}

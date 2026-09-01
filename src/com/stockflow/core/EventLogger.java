package com.stockflow.core;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * EventLogger — append-only event log. Thread-safe; notifies UI listeners
 * on the EDT to keep Swing happy.
 */
public class EventLogger {
    public enum Level { INFO, WARN, ERROR }
    public interface Listener { void onEvent(long ts, Level level, String text); }

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final List<String> history = new ArrayList<>();
    private final int maxHistory = 2000;

    public void addListener(Listener l) { listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public void log(Level level, String fmt, Object... args) {
        String text = String.format(fmt, args);
        long ts = System.currentTimeMillis();
        synchronized (history) {
            history.add(String.format("[%tT] %s %s", ts, level, text));
            if (history.size() > maxHistory) history.subList(0, history.size() - maxHistory).clear();
        }
        // Fire on EDT so the UI can append directly to a JTextArea.
        SwingUtilities.invokeLater(() -> {
            for (Listener l : listeners) l.onEvent(ts, level, text);
        });
    }

    public List<String> snapshot() {
        synchronized (history) { return new ArrayList<>(history); }
    }

    public void clear() {
        synchronized (history) { history.clear(); }
        SwingUtilities.invokeLater(() -> {
            for (Listener l : listeners) l.onEvent(0, Level.INFO, "[log cleared]");
        });
    }
}

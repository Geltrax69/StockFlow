package com.stockflow.ui;

import com.stockflow.core.EventLogger;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * EventLogPanel — append-only text area. Fed on the EDT by EventLogger.
 */
public class EventLogPanel extends JPanel implements EventLogger.Listener {
    private final JTextArea area = new JTextArea();
    private final JLabel title = new JLabel("Event Log");

    public EventLogPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Event Log"));
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(area);
        sp.setBorder(new EmptyBorder(0, 0, 0, 0));
        add(sp, BorderLayout.CENTER);
    }

    @Override
    public void onEvent(long ts, EventLogger.Level level, String text) {
        if (ts == 0) {
            area.setText("");
            return;
        }
        area.append(String.format("[%s] %s%n", level, text));
        // Cap the text area size.
        if (area.getLineCount() > 1500) {
            int lines = area.getLineCount();
            try { area.replaceRange("", area.getLineStartOffset(0), area.getLineEndOffset(lines - 1000)); }
            catch (Exception ignored) {}
        }
        area.setCaretPosition(area.getDocument().getLength());
    }
}

package com.collegebus.gui;

import java.awt.Color;
import java.awt.Graphics;
import javax.swing.*;

/** Snackbar notification that slides up from the bottom of a window, waits, then slides away. */
public class Toast extends JComponent {
    private final String title, message;
    private final boolean success;

    private Toast(String title, String message, boolean success) {
        this.title = title;
        this.message = message;
        this.success = success;
    }

    public static void show(JRootPane root, String title, String message, boolean success) {
        Toast t = new Toast(title, message, success);
        JLayeredPane layer = root.getLayeredPane();
        int w = 420, h = 92, x = layer.getWidth() - w - 28, endY = layer.getHeight() - h - 28;
        t.setBounds(x, layer.getHeight(), w, h);
        layer.add(t, JLayeredPane.POPUP_LAYER);
        long start = System.currentTimeMillis();
        Timer timer = new Timer(15, null);
        timer.addActionListener(e -> {
            long ms = System.currentTimeMillis() - start;
            double in = Math.min(1, ms / 260.0), out = ms > 3600 ? Math.min(1, (ms - 3600) / 260.0) : 0;
            double p = 1 - Math.pow(1 - in, 3) - out * out;            // ease-out in, ease-in out
            t.setLocation(x, (int) (layer.getHeight() - (layer.getHeight() - endY) * p));
            if (out >= 1) {
                timer.stop();
                layer.remove(t);
                layer.repaint();
            }
        });
        timer.start();
    }

    @Override protected void paintComponent(Graphics g) {
        Theme.smooth(g);
        g.setColor(Theme.INK);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
        g.setColor(success ? Theme.GO : Theme.SIGNAL);
        g.fillOval(20, 22, 12, 12);
        g.setColor(Color.WHITE);
        g.setFont(Theme.semi(15));
        g.drawString(title, 46, 33);
        g.setColor(new Color(0xC9C5BC));
        g.setFont(Theme.body(13));
        String line = "";
        int y = 54;
        for (String word : message.split(" ")) {          // simple word wrap, at most two lines
            if (g.getFontMetrics().stringWidth(line + word) > getWidth() - 66 && y < 70) {
                g.drawString(line, 46, y);
                line = "";
                y += 18;
            }
            line += word + " ";
        }
        g.drawString(line, 46, y);
    }
}

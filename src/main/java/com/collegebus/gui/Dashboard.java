package com.collegebus.gui;

import com.collegebus.model.User;
import com.collegebus.service.CollegeBusSystem;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;

/**
 * ABSTRACTION: common window for both dashboards - the route-line menu, page switching, toasts and logout.
 * Subclasses only decide what each page shows by overriding open() (POLYMORPHISM).
 */
public abstract class Dashboard extends JFrame {
    protected final CollegeBusSystem system;
    protected final User user;
    private final String[] menu;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, JComponent> pages = new HashMap<>();
    private String institution;
    private String current;
    private int hover = -1;

    protected Dashboard(User user, CollegeBusSystem system, String[] menu) {
        super("CAMPUS BUS · " + user.getRole());
        this.user = user;
        this.system = system;
        this.menu = menu;
        this.institution = system.institutionName();
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        Theme.fit(this, 1320, 820);
        add(new RouteRail(), BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
    }

    /** Opens the first menu page when the window first appears (after the subclass has built its pages). */
    @Override
    public void setVisible(boolean visible) {
        if (visible && current == null) select(menu[0]);
        super.setVisible(visible);
    }

    /** Called when a menu stop is chosen; each dashboard shows its own page. */
    protected abstract void open(String page);

    public void select(String page) {
        current = page;
        repaint();
        open(page);
    }

    /** Highlights a menu item without reopening it (used when the web page navigates by itself). */
    public void highlight(String page) {
        current = page;
        repaint();
    }

    /** After the admin renames the institution: the menu shows the new name at once. */
    protected void setInstitution(String name) {
        institution = name;
        setTitle(getTitle());
        repaint();
    }

    protected void showPage(String key, JComponent page) {
        JComponent old = pages.put(key, page);
        if (old != page) {
            if (old != null) content.remove(old);
            content.add(page, key);
        }
        cards.show(content, key);
    }

    public void toast(String title, String message, boolean ok) {
        SwingUtilities.invokeLater(() -> Toast.show(getRootPane(), title, message, ok));
    }

    private void logout() {
        dispose();
        new LoginFrame(system).setVisible(true);
    }

    /** The menu drawn as a vertical route: every page is a stop, the open page is the filled vermilion stop. */
    private class RouteRail extends JComponent {
        private static final int TOP = 150, X = 40;

        /** Space between menu stops: 58 px, less on a short window so the last stop stays above the account block. */
        private int gap() { return Math.max(30, Math.min(58, (getHeight() - 110 - 24 - TOP) / Math.max(1, menu.length - 1))); }

        RouteRail() {
            setPreferredSize(new Dimension(240, 0));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int i = index(e);
                    if (i >= 0 && i < menu.length) select(menu[i]);
                    else if (e.getY() > getHeight() - 60) logout();
                }
                @Override public void mouseMoved(MouseEvent e) { hover = index(e); repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        private int index(MouseEvent e) {
            int i = Math.round((e.getY() - TOP) / (float) gap());
            return Math.abs(e.getY() - (TOP + i * gap())) < 24 && i >= 0 && i < menu.length ? i : -1;
        }

        /** Up to two lines, word-wrapped; a third line's worth is cut with an ellipsis. */
        private void drawWrapped(Graphics2D g, String text, int x, int y, int width) {
            var fm = g.getFontMetrics();
            String line = "";
            int lines = 0;
            for (String word : text.split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (fm.stringWidth(next) <= width || line.isEmpty()) { line = next; continue; }
                if (++lines == 2) { g.drawString(line + "…", x, y); return; }
                g.drawString(line, x, y);
                y += fm.getHeight();
                line = word;
            }
            g.drawString(line, x, y);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            Theme.smooth(g);
            g.setColor(Theme.PLUM);
            g.fillRect(0, 0, getWidth(), getHeight());
            Theme.drawMark(g, 28, 36, 28, Theme.SURFACE, Theme.SIGNAL);
            g.setColor(Theme.SURFACE);
            g.setFont(Theme.display(19));
            g.drawString("CAMPUS BUS", 66, 57);
            g.setFont(Theme.body(12));
            g.setColor(new Color(0xB9A9B6));
            drawWrapped(g, institution, 66, 76, getWidth() - 80);

            g.setColor(Theme.PLUM_LIGHT);
            g.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(X, TOP, X, TOP + (menu.length - 1) * gap());
            for (int i = 0; i < menu.length; i++) {
                int y = TOP + i * gap();
                boolean on = menu[i].equals(current);
                g.setColor(on ? Theme.SIGNAL : Theme.PLUM);
                g.fillOval(X - (on ? 9 : 7), y - (on ? 9 : 7), on ? 18 : 14, on ? 18 : 14);
                g.setColor(on ? Theme.SURFACE : new Color(0x9C8A99));
                g.setStroke(new BasicStroke(3));
                g.drawOval(X - (on ? 9 : 7), y - (on ? 9 : 7), on ? 18 : 14, on ? 18 : 14);
                g.setFont(on ? Theme.semi(16) : Theme.body(16));
                g.setColor(on || i == hover ? Theme.SURFACE : new Color(0xC9BBC6));
                g.drawString(menu[i], X + 26, y + 6);
            }
            g.setColor(Theme.PLUM_LIGHT);
            g.fillRect(0, getHeight() - 110, getWidth(), 1);
            g.setColor(Theme.SURFACE);
            g.setFont(Theme.semi(15));
            g.drawString(user.getName(), 28, getHeight() - 76);
            g.setFont(Theme.body(13));
            g.setColor(new Color(0xB9A9B6));
            g.drawString(user.getEmail(), 28, getHeight() - 56);
            g.setColor(Theme.SURFACE);
            g.setFont(Theme.semi(13));
            g.drawString("Log out", 28, getHeight() - 24);
        }
    }
}

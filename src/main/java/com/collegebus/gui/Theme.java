package com.collegebus.gui;

import java.awt.*;
import java.awt.geom.Arc2D;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.Border;

/** One place for the visual identity: colours, fonts (Overpass - descended from highway signage), buttons and the logo. */
public final class Theme {
    public static final Color PAPER = new Color(0xE9E7E0), SURFACE = new Color(0xF5F4EF), INK = new Color(0x1D1D1B),
            MUTED = new Color(0x6E6A63), LINE = new Color(0xCDC9BE), SIGNAL = new Color(0xE8431B), GO = new Color(0xB8E340),
            AMBER = new Color(0xF2B705), PLUM = new Color(0x2E1A2E), PLUM_LIGHT = new Color(0x4C3349), CANCEL = new Color(0xA9564B);
    private static final Map<String, Font> FONTS = new HashMap<>();

    private Theme() { }

    private static Font font(String file, float size) {
        Font base = FONTS.computeIfAbsent(file, f -> {
            try (var in = Theme.class.getResourceAsStream("/web/assets/fonts/" + f + ".ttf")) {
                return Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (Exception e) {
                return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            }
        });
        return base.deriveFont(size);
    }

    public static Font display(float s) { return font("Overpass-ExtraBold", s); }
    public static Font semi(float s) { return font("Overpass-SemiBold", s); }
    public static Font body(float s) { return font("Overpass-Regular", s); }
    public static Font mono(float s) { return font("OverpassMono-Bold", s); }

    /** Applies fonts and colours to standard Swing components (JTable, JComboBox, JOptionPane ...). */
    public static void install() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        for (String k : new String[]{"Label", "TextField", "PasswordField", "ComboBox", "Table", "TableHeader", "Spinner", "OptionPane", "Button", "List"})
            UIManager.put(k + ".font", body(14));
        UIManager.put("Panel.background", PAPER);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("ComboBox.background", SURFACE);
        UIManager.put("ComboBox.selectionBackground", INK);
        UIManager.put("Table.selectionBackground", new Color(0xE3D9E2));
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        for (String k : new String[]{"thumb", "thumbHighlight", "thumbShadow", "thumbDarkShadow"}) UIManager.put("ScrollBar." + k, LINE);   // no Metal blue
        UIManager.put("ScrollBar.track", SURFACE);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("OptionPane.messageFont", body(16));
        UIManager.put("OptionPane.buttonFont", semi(14));
        UIManager.put("OptionPane.border", BorderFactory.createEmptyBorder(22, 26, 18, 26));
        UIManager.put("OptionPane.buttonAreaBorder", BorderFactory.createEmptyBorder(18, 0, 0, 0));
        UIManager.put("Button.gradient", null);                 // flat buttons inside JOptionPane
        UIManager.put("Button.background", SURFACE);
        UIManager.put("Button.select", LINE);
        UIManager.put("Button.border", BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(INK, 2),
                BorderFactory.createEmptyBorder(8, 18, 6, 18)));
    }

    /** Fits a window on small laptop screens (e.g. 1366x768 with a taskbar). */
    public static void fit(java.awt.Window w, int width, int height) {
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        w.setMinimumSize(new Dimension(Math.min(1024, screen.width), Math.min(640, screen.height)));
        w.setSize(Math.min(width, screen.width), Math.min(height, screen.height));
        w.setLocationRelativeTo(null);
    }

    public static void smooth(Graphics g) {
        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    public static JLabel label(String text, Font f, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(f);
        l.setForeground(c);
        return l;
    }

    /** Text input styled like a form on a ticket: only an underline, generous padding. */
    public static <T extends JTextField> T field(T f) {
        Border line = BorderFactory.createMatteBorder(0, 0, 2, 0, INK);
        f.setBorder(BorderFactory.createCompoundBorder(line, BorderFactory.createEmptyBorder(8, 2, 6, 2)));
        f.setFont(body(16));
        f.setOpaque(false);
        f.setCaretColor(SIGNAL);
        return f;
    }

    /** Drop-down with the same underline style as text fields and a flat arrow. */
    public static <T extends javax.swing.JComponent> T combo(T c) {
        if (c instanceof javax.swing.JComboBox<?> box) box.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton b = new javax.swing.plaf.basic.BasicArrowButton(javax.swing.SwingConstants.SOUTH, SURFACE, SURFACE, INK, SURFACE);
                b.setBorder(BorderFactory.createEmptyBorder());
                return b;
            }
        });
        c.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, INK));
        c.setBackground(SURFACE);
        c.setFont(body(15));
        if (c instanceof javax.swing.JComboBox<?> box && box.isEditable()                  // typed departments: same look as the closed box
                && box.getEditor().getEditorComponent() instanceof JTextField editor) {
            editor.setBorder(BorderFactory.createEmptyBorder(6, 2, 4, 2));
            editor.setBackground(SURFACE);
            editor.setFont(body(15));
            editor.setCaretColor(SIGNAL);
        }
        c.setPreferredSize(new Dimension(Math.max(170, c.getPreferredSize().width), 40));
        return c;
    }

    /** Flat button: primary = vermilion fill, secondary = ink outline. */
    public static JButton button(String text, boolean primary) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                smooth(g);
                boolean hover = getModel().isRollover(), press = getModel().isPressed();
                Color fill = primary ? (press ? SIGNAL.darker() : hover ? new Color(0xD13A15) : SIGNAL) : (hover ? INK : new Color(0, 0, 0, 0));
                g.setColor(isEnabled() ? fill : LINE);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                if (!primary) {
                    g.setColor(INK);
                    ((Graphics2D) g).setStroke(new BasicStroke(1.5f));
                    g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 6, 6);
                }
                setForeground(primary || hover ? Color.WHITE : INK);
                super.paintComponent(g);
            }
        };
        b.setFont(semi(14));
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(Math.max(110, text.length() * 9 + 40), 42));
        b.setMaximumSize(b.getPreferredSize());
        return b;
    }

    /** The CAMPUS BUS mark: a route line bent into a "C" that ends at a destination stop. */
    public static void drawMark(Graphics2D g, int x, int y, int size, Color line, Color stop) {
        smooth(g);
        float w = size * 0.16f;
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(line);
        g.draw(new Arc2D.Float(x + w, y + w, size - 2 * w, size - 2 * w, 45, 270, Arc2D.OPEN));
        int d = Math.round(size * 0.34f);
        double ang = Math.toRadians(45), r = (size - 2 * w) / 2;
        int cx = (int) (x + size / 2.0 + r * Math.cos(ang)), cy = (int) (y + size / 2.0 - r * Math.sin(ang));
        g.setColor(stop);
        g.fillOval(cx - d / 2, cy - d / 2, d, d);
    }
}

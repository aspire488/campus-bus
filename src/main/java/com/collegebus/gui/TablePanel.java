package com.collegebus.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;

/**
 * Generic admin table (works for any type T - buses, routes, students, reservations):
 * title, search box, optional filter, sortable JTable with status pills and occupancy bars.
 */
public class TablePanel<T> extends JPanel {
    private final DefaultTableModel model;
    private final JTable table;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final Function<T, Object[]> toRow;
    private final JTextField search = Theme.field(new JTextField(18));
    private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    private final JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));      // search + filter, under the title
    private final JLabel count = Theme.label("", Theme.body(15), Theme.MUTED);
    /** One drop-down filter: its combo, the column it filters, and whether its choices come from the rows. */
    private record Filter(JComboBox<String> box, int column, boolean fromData) { }
    private final List<Filter> filters = new ArrayList<>();
    private String caption = "";
    private JComboBox<String> chooser;
    private JScrollPane scroll;
    private List<T> items = new ArrayList<>();

    public TablePanel(String title, String[] columns, Function<T, Object[]> toRow) {
        super(new BorderLayout(0, 18));
        this.toRow = toRow;
        setBorder(BorderFactory.createEmptyBorder(40, 48, 32, 48));
        model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int c) {        // so numbers sort as numbers
                return getRowCount() > 0 && getValueAt(0, c) != null ? getValueAt(0, c).getClass() : Object.class;
            }
        };
        table = new JTable(model);
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.setRowHeight(44);
        table.setShowVerticalLines(false);
        table.setGridColor(Theme.LINE);
        table.setBackground(Theme.SURFACE);
        Cell cell = new Cell();
        for (int i = 0; i < columns.length; i++) table.getColumnModel().getColumn(i).setCellRenderer(cell);
        DefaultTableCellRenderer header = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                super.getTableCellRendererComponent(t, v, false, false, r, c);
                setFont(Theme.semi(12));
                setForeground(Theme.MUTED);
                setBackground(Theme.PAPER);
                setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
                return this;
            }
        };
        table.getTableHeader().setDefaultRenderer(header);
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.INK));
        scroll = new JScrollPane(table);
        scroll.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { fitColumns(); }
        });
        scroll.getViewport().setBackground(Theme.SURFACE);

        JPanel head = new JPanel(new BorderLayout(0, 14));             // title + count on top, tools and buttons on their own row
        JPanel titles = new JPanel(new BorderLayout(0, 4));
        titles.add(Theme.label(title, Theme.display(40), Theme.INK), BorderLayout.NORTH);
        titles.add(count, BorderLayout.SOUTH);
        JPanel bar = new JPanel(new BorderLayout(0, 10));
        bar.add(tools, BorderLayout.WEST);
        bar.add(actions, BorderLayout.EAST);
        bar.addComponentListener(new java.awt.event.ComponentAdapter() {           // narrow window: buttons on their own row
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                boolean narrow = tools.getPreferredSize().width + actions.getPreferredSize().width + 24 > bar.getWidth();
                Object where = ((BorderLayout) bar.getLayout()).getConstraints(actions);
                if (narrow == BorderLayout.EAST.equals(where)) {
                    bar.remove(actions);
                    bar.add(actions, narrow ? BorderLayout.SOUTH : BorderLayout.EAST);
                    bar.revalidate();
                }
            }
        });
        head.add(titles, BorderLayout.NORTH);
        head.add(bar, BorderLayout.SOUTH);
        ((FlowLayout) tools.getLayout()).setAlignOnBaseline(true);
        tools.add(Theme.label("Search", Theme.semi(12), Theme.MUTED));
        tools.add(search);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        add(head, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    /** Adds a drop-down that keeps only rows whose {@code column} equals the chosen value.
     *  With only the "All ..." label, the choices are taken from the rows themselves (e.g. each institution's own departments). */
    public TablePanel<T> withFilter(int column, String... options) {
        JComboBox<String> box = Theme.combo(new JComboBox<>(options));
        box.addActionListener(e -> applyFilter());
        filters.add(new Filter(box, column, options.length == 1));
        tools.add(box);
        return this;
    }

    /** A drop-down that asks the page to reload (e.g. which dates to load), instead of filtering rows already loaded. */
    public TablePanel<T> withChooser(String[] options, Runnable reload) {
        chooser = Theme.combo(new JComboBox<>(options));
        chooser.setPreferredSize(new java.awt.Dimension(240, 40));
        chooser.addActionListener(e -> reload.run());
        tools.add(chooser, 0);
        return this;
    }

    /** The chooser's current option. */
    public String chosen() { return String.valueOf(chooser.getSelectedItem()); }

    public TablePanel<T> withButton(String text, boolean primary, Runnable action) {
        JButton b = Theme.button(text, primary);
        b.addActionListener(e -> action.run());
        actions.add(b);
        return this;
    }

    /** Extra line of context after the row count (e.g. the timetable on Schedules). */
    public void setCaption(String text) {
        caption = text;
        applyFilter();
    }

    public void setItems(List<T> list) {
        items = list;
        model.setRowCount(0);
        for (T item : list) model.addRow(toRow.apply(item));
        for (Filter f : filters) {
            if (!f.fromData()) continue;
            Object chosen = f.box().getSelectedItem();
            java.util.TreeSet<String> values = new java.util.TreeSet<>();
            for (int r = 0; r < model.getRowCount(); r++) values.add(String.valueOf(model.getValueAt(r, f.column())));
            DefaultComboBoxModel<String> m = new DefaultComboBoxModel<>();
            m.addElement(f.box().getItemAt(0));
            values.forEach(m::addElement);
            f.box().setModel(m);
            f.box().setSelectedItem(values.contains(String.valueOf(chosen)) ? chosen : m.getElementAt(0));
        }
        applyFilter();
        fitColumns();
    }

    /**
     * Sizes every column to its widest header or cell (first 300 rows), so long route and stop names are not cut.
     * If everything fits, the columns share the width; if not, the table scrolls sideways instead of clipping text.
     */
    private void fitColumns() {
        int total = 0;
        for (int c = 0; c < table.getColumnCount(); c++) {
            var col = table.getColumnModel().getColumn(c);
            var head = table.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(table, col.getHeaderValue(), false, false, -1, c);
            int w = head.getPreferredSize().width;
            for (int r = 0; r < Math.min(table.getRowCount(), 300); r++)
                w = Math.max(w, table.prepareRenderer(table.getCellRenderer(r, c), r, c).getPreferredSize().width);
            w = Math.min(w + 24, 420);                                     // padding; very long text still gets a sensible cap
            col.setPreferredWidth(w);
            total += w;
        }
        int room = scroll.getViewport().getWidth();
        table.setAutoResizeMode(room == 0 || total <= room ? JTable.AUTO_RESIZE_ALL_COLUMNS : JTable.AUTO_RESIZE_OFF);
    }

    /** The object behind the selected row, or null (works even when the table is sorted/filtered). */
    public T selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : items.get(table.convertRowIndexToModel(row));
    }

    private void applyFilter() {
        List<RowFilter<Object, Object>> rules = new ArrayList<>();
        rules.add(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(search.getText().trim())));
        for (Filter f : filters)
            if (f.box().getSelectedIndex() > 0)
                rules.add(RowFilter.regexFilter("^" + java.util.regex.Pattern.quote(String.valueOf(f.box().getSelectedItem())) + "$", f.column()));
        sorter.setRowFilter(RowFilter.andFilter(rules));
        count.setText(items.isEmpty() ? "Nothing here yet." : table.getRowCount() == 0 ? "Nothing matches. Clear the search or filter."
                : table.getRowCount() + " of " + items.size() + " shown" + (caption.isEmpty() ? "" : "   ·   " + caption));
    }

    /** Draws status words as coloured pills. */
    private class Cell extends DefaultTableCellRenderer {
        private Object value;

        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, false, row, col);
            value = v;
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            setFont(v instanceof Number || String.valueOf(v).matches("\\d\\d:\\d\\d(–\\d\\d:\\d\\d)?|CB-?\\d+|CBR\\d+|\\d{4}-\\d\\d-\\d\\d") ? Theme.mono(14) : Theme.body(14));
            return this;
        }

        @Override protected void paintComponent(Graphics g) {
            Color pill = switch (String.valueOf(value)) {
                case "ACTIVE", "BOOKED", "UPCOMING" -> Theme.GO;
                case "MAINTENANCE", "CURRENT" -> Theme.AMBER;
                case "CANCELLED" -> Theme.CANCEL;
                case "COMPLETED" -> Theme.LINE;
                default -> null;
            };
            if (pill == null) { super.paintComponent(g); return; }
            Theme.smooth(g);
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            String text = String.valueOf(value).charAt(0) + String.valueOf(value).substring(1).toLowerCase();
            g.setFont(Theme.semi(12));
            int tw = g.getFontMetrics().stringWidth(text);
            g.setColor(pill);
            g.fillRoundRect(12, 11, tw + 22, 22, 22, 22);
            g.setColor(pill == Theme.CANCEL ? Color.WHITE : Theme.INK);
            g.drawString(text, 23, 27);
        }
    }
}

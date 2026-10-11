package com.collegebus.gui;

import java.awt.*;
import java.sql.SQLException;
import javax.swing.*;

/** Reusable modal form (JDialog): add labelled fields, then open() runs the save action and shows any error inline. */
public class FormDialog extends JDialog {
    /** The code to run when the user presses the save button. */
    public interface SaveAction { void save() throws Exception; }

    private final JPanel fields = new JPanel(new GridLayout(0, 2, 28, 16));   // two columns of label + field
    private final JLabel error = Theme.label(" ", Theme.semi(13), Theme.SIGNAL);
    private final JPanel bottom = new JPanel(new BorderLayout(0, 12));
    private final JPanel head = new JPanel(new BorderLayout(0, 6));
    private boolean saved;

    public FormDialog(Window owner, String title) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(Theme.SURFACE);
        root.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(6, 0, 0, 0, Theme.SIGNAL),
                BorderFactory.createEmptyBorder(22, 30, 22, 30)));
        head.setOpaque(false);
        head.add(Theme.label(title, Theme.display(24), Theme.INK), BorderLayout.NORTH);
        root.add(head, BorderLayout.NORTH);
        fields.setOpaque(false);
        root.add(fields, BorderLayout.CENTER);
        bottom.setOpaque(false);
        bottom.add(error, BorderLayout.NORTH);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);
    }

    /** One line of explanation under the title. */
    public void note(String text) {
        head.add(Theme.label(text, Theme.body(14), Theme.MUTED), BorderLayout.SOUTH);
    }

    /** Leaves one grid cell empty, so related fields (password + repeat) share a row. */
    public void skip() { fields.add(new JLabel()); }

    /** Adds a field under a small label and returns it, so callers can keep a reference. */
    public <T extends JComponent> T add(String label, T field) {
        if (field instanceof JTextField t) Theme.field(t);
        else Theme.combo(field);
        JPanel cell = new JPanel(new BorderLayout(0, 2));
        cell.setOpaque(false);
        cell.add(Theme.label(label, Theme.semi(12), Theme.MUTED), BorderLayout.NORTH);
        cell.add(field, BorderLayout.CENTER);
        fields.add(cell);
        return field;
    }

    /** A plain "Password" field with the rule as a small hint underneath. */
    public JPasswordField password() {
        JPasswordField pw = add("Password", new JPasswordField());
        ((JPanel) pw.getParent()).add(Theme.label("At least 4 characters", Theme.body(11), Theme.MUTED), BorderLayout.SOUTH);
        return pw;
    }

    /** Shows the dialog; returns true only when the save action finished without an error. */
    public boolean open(String saveText, SaveAction action) {
        JButton save = Theme.button(saveText, true), cancel = Theme.button("Cancel", false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(save);
        bottom.add(buttons, BorderLayout.SOUTH);
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> {
            if (saved) return;                                   // a queued second click must not save twice
            try {
                action.save();
                saved = true;
                dispose();
            } catch (SQLException ex) {
                error.setText("Not saved: the database is unavailable. Check that MySQL is running.");
            } catch (NumberFormatException | java.time.format.DateTimeParseException ex) {
                error.setText("Check the numbers and times (use 24-hour HH:MM).");
            } catch (Exception ex) {
                error.setText(ex.getMessage());
            }
        });
        getRootPane().setDefaultButton(save);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);   // Escape = Cancel
        pack();
        setSize(Math.max(getWidth(), 640), getHeight());
        setLocationRelativeTo(getOwner());
        setVisible(true);
        return saved;
    }
}

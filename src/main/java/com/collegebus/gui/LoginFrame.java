package com.collegebus.gui;

import com.collegebus.model.Student;
import com.collegebus.model.User;
import com.collegebus.service.CollegeBusSystem;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;
import javax.swing.*;

/** First screen: a route-line hero on the left, sign-in on the right. */
public class LoginFrame extends JFrame {
    /**
     * Class suggestions in the form semester + department + division, e.g. "S3 CS2": CS, CT (CS-AI), CO (CS-Data Science), EC,
     * ME (Mechanical), CE (Civil), AU (Automobile), VL (VLSI). The field stays editable: any institution can type its own classes.
     */
    static final String[] DEPARTMENTS = classes();

    private static String[] classes() {
        java.util.List<String> list = new java.util.ArrayList<>();
        int first = java.time.LocalDate.now().getMonthValue() >= 7 ? 1 : 2;   // odd semesters Jul-Dec, even Jan-Jun
        for (String dept : new String[]{"CS", "CT", "CO", "EC", "ME", "CE", "AU", "VL"})
            for (int sem = first; sem <= 8; sem += 2) list.add("S" + sem + " " + dept + "1");
        return list.toArray(new String[0]);
    }

    private final CollegeBusSystem system;
    private final JTextField login = Theme.field(new JTextField());
    private final JPasswordField password = Theme.field(new JPasswordField());
    private final JLabel loginLabel = Theme.label("Email or admission number", Theme.semi(12), Theme.MUTED);
    private final JLabel error = Theme.label(" ", Theme.semi(13), Theme.SIGNAL);
    private final JButton go = Theme.button("Continue", true);
    private final JLabel heading = Theme.label("Student sign in", Theme.display(34), Theme.INK);
    private final JButton register = Theme.button("Create a student account", false);
    private final JLabel adminNote = Theme.label("New admins are added inside: Admins > Add admin.", Theme.body(13), Theme.MUTED);
    private String institution;
    private boolean admin;
    private float busProgress;            // 0..1 position of the bus on the hero route line
    private String liveLine;

    public LoginFrame(CollegeBusSystem system) {
        super("CAMPUS BUS");
        this.system = system;
        this.institution = system.institutionName();
        setTitle("CAMPUS BUS · " + institution);
        try {
            Map<String, Object> d = system.displayDetails();
            liveLine = d.get("freeToday") + " seats open on " + d.get("activeBuses") + " buses today";
        } catch (Exception e) {
            liveLine = null;
            error.setText(com.collegebus.util.DatabaseConnection.explain(e));
        }
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        Theme.fit(this, 1240, 760);
        add(new Hero(), BorderLayout.CENTER);
        add(form(), BorderLayout.EAST);
        Timer t = new Timer(16, null);       // one orchestrated moment: the bus drives to the institution, then stops
        t.addActionListener(e -> {
            busProgress = Math.min(1, busProgress + 0.008f);
            repaint();
            if (busProgress >= 1) t.stop();
        });
        t.start();
    }

    private JPanel form() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Theme.SURFACE);
        p.setPreferredSize(new Dimension(430, 0));
        p.setBorder(BorderFactory.createEmptyBorder(0, 48, 0, 48));
        go.addActionListener(e -> signIn());
        register.addActionListener(e -> register());
        login.addActionListener(e -> password.requestFocus());
        password.addActionListener(e -> signIn());
        adminNote.setVisible(admin);
        Component[] items = {Box.createVerticalGlue(), new RoleSwitch(), Box.createVerticalStrut(34),
            heading, Box.createVerticalStrut(22), loginLabel, login,
            Box.createVerticalStrut(18), Theme.label("Password", Theme.semi(12), Theme.MUTED), password,
            Box.createVerticalStrut(14), error, Box.createVerticalStrut(14), go,
            Box.createVerticalStrut(40), register, adminNote, Box.createVerticalGlue()};
        for (Component c : items) {
            ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
            if (c instanceof JTextField || c instanceof JButton) c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            if (c instanceof JLabel) c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));   // long messages are never cut off
            p.add(c);
        }
        return p;
    }

    /** Checks the password on a background thread so the window never freezes; repeated clicks are ignored meanwhile. */
    private void signIn() {
        if (!go.isEnabled()) return;
        go.setEnabled(false);
        go.setText("Signing in…");
        error.setText(" ");
        boolean asAdmin = admin;
        String who = login.getText(), pw = new String(password.getPassword());
        new SwingWorker<User, Void>() {
            @Override protected User doInBackground() throws Exception { return system.login(asAdmin, who, pw); }

            @Override protected void done() {
                go.setEnabled(true);
                go.setText("Continue");
                try {
                    JFrame dashboard = get().openDashboard(system);       // Student or Admin decides (polymorphism)
                    dashboard.setVisible(true);
                    dispose();
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    error.setText(cause instanceof IllegalArgumentException ? cause.getMessage() : com.collegebus.util.DatabaseConnection.explain(cause));
                    password.selectAll();
                    password.requestFocus();
                }
            }
        }.execute();
    }

    private void register() {
        FormDialog f = new FormDialog(this, "Create a student account");
        JTextField reg = f.add("Admission number, e.g. SCS/10001/26", new JTextField()), name = f.add("Full name", new JTextField());
        JTextField email = f.add("Email", new JTextField()), phone = f.add("Phone (10 digits)", new JTextField());
        JComboBox<String> dept = f.add("Class, e.g. S3 CS2", departments());
        f.skip();
        JPasswordField pw = f.add("Password (8+: A-Z, a-z, 0-9 and a symbol)", new JPasswordField()), again = f.add("Repeat password", new JPasswordField());
        boolean ok = f.open("Create account", () -> {
            if (!new String(pw.getPassword()).equals(new String(again.getPassword()))) throw new IllegalArgumentException("The two passwords are different.");
            Student s = new Student();
            s.registerStudent(reg.getText(), name.getText(), email.getText(), new String(pw.getPassword()), typed(dept), phone.getText());
            system.registerStudent(s);
        });
        if (ok) {
            selectRole(false);
            login.setText(email.getText().trim().toLowerCase());
            password.requestFocus();
            Toast.show(getRootPane(), "Account created", "Sign in with your new password.", true);
        }
    }

    /** Editable drop-down: pick a suggestion or type your own department, class or programme. */
    static JComboBox<String> departments() {
        JComboBox<String> box = new JComboBox<>(DEPARTMENTS);
        box.setEditable(true);
        return box;
    }

    /** The class as typed, even if the editor has not committed it to the selection yet. */
    static String typed(JComboBox<String> box) { return String.valueOf(box.getEditor().getItem()).trim(); }

    /** True when the database has no administrator yet (a new installation). A database error counts as "no": the login screen reports it. */
    public static boolean needsSetup(CollegeBusSystem system) {
        try {
            return system.needsSetup();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * First run only, in its own window before the login screen: the institution names itself and creates its first administrator.
     * Returns the new admin's username, or null if the window was closed. Later admins are added inside the console.
     */
    public static String setUpInstitution(CollegeBusSystem system) {
        FormDialog f = new FormDialog(null, "Set up your institution");
        f.note("First-time setup. Create the administrator account for your school, college or university. This screen appears only once.");
        JTextField inst = f.add("Institution name", new JTextField(28)), name = f.add("Your full name", new JTextField());
        JTextField user = f.add("Admin username", new JTextField());
        f.skip();
        JPasswordField pw = f.add("Password (8+: A-Z, a-z, 0-9 and a symbol)", new JPasswordField()), again = f.add("Repeat password", new JPasswordField());
        boolean ok = f.open("Create administrator", () -> {
            String p = new String(pw.getPassword());
            if (!p.equals(new String(again.getPassword()))) throw new IllegalArgumentException("The two passwords are different.");
            system.setUpInstitution(inst.getText(), user.getText(), name.getText(), p);
        });
        return ok ? user.getText().trim().toLowerCase() : null;
    }

    /** After setup: open on the Admin side with the new username filled in. */
    public void welcomeAdmin(String username) {
        selectRole(true);
        login.setText(username);
        SwingUtilities.invokeLater(() -> {
            password.requestFocus();
            Toast.show(getRootPane(), "Administrator created", "Sign in to add buses, routes and more admins.", true);
        });
    }

    /** Students and admins sign in separately: each side has its own label and account action; nothing carries over. */
    private void selectRole(boolean asAdmin) {
        if (asAdmin != admin) { login.setText(""); password.setText(""); }
        admin = asAdmin;
        heading.setText(admin ? "Admin sign in" : "Student sign in");
        loginLabel.setText(admin ? "Username" : "Email or admission number");
        register.setVisible(!admin);
        adminNote.setVisible(admin);                  // students sign themselves up; admins never do (first one at setup, the rest inside the console)
        error.setText(" ");
        revalidate();
        repaint();
    }

    /** Two-part switch: Student | Admin. */
    private class RoleSwitch extends JComponent {
        RoleSwitch() {
            setPreferredSize(new Dimension(220, 38));
            setMaximumSize(new Dimension(220, 38));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { selectRole(e.getX() > getWidth() / 2); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Theme.smooth(g);
            int half = getWidth() / 2;
            g.setColor(Theme.INK);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            g.fillRoundRect(admin ? half : 0, 0, half, getHeight(), 6, 6);
            g.setFont(Theme.semi(14));
            g.setColor(admin ? Theme.INK : Theme.SURFACE);
            g.drawString("Student", 30, 24);
            g.setColor(admin ? Theme.SURFACE : Theme.INK);
            g.drawString("Admin", half + 34, 24);
        }
    }

    /** Left side: wordmark, headline and a generic route (your stop -> the institution) with a bus driving along it. */
    private class Hero extends JComponent {
        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            Theme.smooth(g);
            int w = getWidth(), h = getHeight(), left = 64;
            g.setColor(Theme.PAPER);
            g.fillRect(0, 0, w, h);
            Theme.drawMark(g, left, 48, 40, Theme.INK, Theme.SIGNAL);
            g.setColor(Theme.INK);
            g.setFont(Theme.display(26));
            g.drawString("CAMPUS BUS", left + 52, 78);
            g.setFont(Theme.body(14));
            g.setColor(Theme.MUTED);
            g.drawString(institution, left + 52, 100);

            g.setColor(Theme.INK);
            g.setFont(Theme.display(Math.min(96, w / 8f)));
            int lh = (int) (Math.min(96, w / 8f) * 0.98), top = (int) (h * 0.36);
            g.drawString("Pick a bus.", left - 4, top);
            g.drawString("Pick a seat.", left - 4, top + lh);
            g.drawString("Ride.", left - 4, top + 2 * lh);

            int y = (int) (h * 0.80), x0 = left, x1 = w - 90;              // the route line
            g.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(Theme.INK);
            g.drawLine(x0, y, x1, y);
            String[][] stops = {{"Your stop", "Pick-up"}, {"", ""}, {"", ""}, {institution, "Both ways, every day"}};   // no real place names: works for any institution
            for (int i = 0; i < stops.length; i++) {
                int x = x0 + (x1 - x0) * i / (stops.length - 1);
                boolean end = i == stops.length - 1;
                int r = end ? 15 : 9;
                g.setColor(end ? Theme.SIGNAL : Theme.PAPER);
                g.fillOval(x - r, y - r, 2 * r, 2 * r);
                g.setColor(Theme.INK);
                g.setStroke(new BasicStroke(4));
                g.drawOval(x - r, y - r, 2 * r, 2 * r);
                g.setFont(end ? Theme.semi(16) : Theme.body(14));
                int tw = g.getFontMetrics().stringWidth(stops[i][0]);
                int tx = end ? x1 - tw + 14 : x0 - 8;
                g.drawString(stops[i][0], tx, y + 44);
                g.setFont(Theme.body(13));
                g.setColor(Theme.MUTED);
                g.drawString(stops[i][1], end ? x1 - g.getFontMetrics().stringWidth(stops[i][1]) + 14 : tx, y + 64);
            }
            float p = 1 - (float) Math.pow(1 - busProgress, 3);        // ease-out
            int bx = (int) (x0 + (x1 - x0 - 40) * p);
            g.setColor(Theme.SIGNAL);
            g.fillRoundRect(bx - 22, y - 46, 50, 26, 8, 8);
            g.setColor(Theme.PAPER);
            g.fillRect(bx - 14, y - 41, 10, 9);
            g.fillRect(bx, y - 41, 10, 9);
            g.fillRect(bx + 14, y - 41, 8, 9);
            if (liveLine != null) {
                g.setColor(Theme.INK);
                g.setFont(Theme.semi(15));
                g.drawString(liveLine, left, (int) (h * 0.36) - (int) (Math.min(96, w / 8f)) - 18);
            }
        }
    }
}

package com.collegebus.gui;

import com.collegebus.model.Student;
import com.collegebus.service.CollegeBusSystem;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.*;

/** Student window: Home, Find a bus and My reservations are HTML pages; Profile is a Swing form. */
public class StudentDashboard extends Dashboard {
    private final Student student;
    private final WebPanel web;

    public StudentDashboard(Student student, CollegeBusSystem system) {
        super(student, system, new String[]{"Home", "Find a bus", "My reservations", "Profile"});
        this.student = student;
        this.web = new WebPanel("student", new WebBridge(this, system, student), "home");
    }

    @Override
    protected void open(String page) {
        switch (page) {
            case "Profile" -> showPage("profile", profile());
            case "Find a bus" -> { showPage("web", web); web.show("search"); }
            case "My reservations" -> { showPage("web", web); web.show("trips"); }
            default -> { showPage("web", web); web.show("home"); }
        }
    }

    /** Swing form for Student.updateStudent(). */
    private JComponent profile() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(56, 64, 40, 64));
        JTextField name = Theme.field(new JTextField(student.getName())), phone = Theme.field(new JTextField(student.getPhone()));
        JComboBox<String> dept = Theme.combo(LoginFrame.departments());
        dept.setSelectedItem(student.getDepartment());
        JLabel error = Theme.label(" ", Theme.semi(13), Theme.SIGNAL);
        JButton save = Theme.button("Save changes", true);
        save.addActionListener(e -> {
            String oldName = student.getName(), oldClass = student.getDepartment(), oldPhone = student.getPhone();
            try {
                student.updateStudent(name.getText(), LoginFrame.typed(dept), phone.getText());
                system.students().update(student);
                error.setText(" ");
                repaint();                                    // the menu shows the (possibly new) name
                toast("Profile saved", "Your details are up to date.", true);
            } catch (Exception ex) {
                if (!student.getName().equals(oldName) || !student.getPhone().equals(oldPhone) || !student.getDepartment().equals(oldClass))
                    student.updateStudent(oldName, oldClass, oldPhone);       // not saved: never show unsaved values as the profile
                error.setText(ex instanceof java.sql.SQLException s ? com.collegebus.util.DatabaseConnection.explain(s) : ex.getMessage());
            }
        });
        JPanel grid = new JPanel(new GridLayout(0, 2, 40, 6));
        grid.setMaximumSize(new Dimension(760, 260));
        String[] labels = {"Admission number", "Email", "Full name", "Phone", "Class (e.g. S3 CS2)", ""};
        JComponent[] values = {Theme.label(student.getRegNo(), Theme.mono(18), Theme.INK), Theme.label(student.getEmail(), Theme.body(17), Theme.INK),
            name, phone, dept, new JLabel()};
        for (int i = 0; i < labels.length; i += 2) {
            grid.add(Theme.label(labels[i], Theme.semi(12), Theme.MUTED));
            grid.add(Theme.label(labels[i + 1], Theme.semi(12), Theme.MUTED));
            grid.add(values[i]);
            grid.add(values[i + 1]);
        }
        for (JComponent c : new JComponent[]{Theme.label("Your details", Theme.display(40), Theme.INK),
            Theme.label("Used on your bus pass and by the transport office.", Theme.body(16), Theme.MUTED),
            (JComponent) Box.createVerticalStrut(36), grid, (JComponent) Box.createVerticalStrut(24), error, save}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            p.add(c);
        }
        return p;
    }
}

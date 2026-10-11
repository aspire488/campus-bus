package com.collegebus.model;

import com.collegebus.gui.StudentDashboard;
import com.collegebus.service.CollegeBusSystem;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JFrame;

/** INHERITANCE: a Student IS-A User, with student-specific details. */
public class Student extends User {
    private String regNo;
    private String department;
    private String phone;
    private int reservationCount;        // filled by StudentDAO for the admin view

    public Student(int id, String regNo, String name, String email, String passwordHash, String department, String phone) {
        super(id, name, email, passwordHash);
        this.regNo = regNo;
        this.department = department;
        this.phone = phone;
    }

    /** Creates an empty student, then registerStudent() fills and validates it. */
    public Student() { super(0, "", "", ""); }

    /** PPT method: validates and stores new-student details. */
    public void registerStudent(String regNo, String name, String email, String password, String department, String phone) {
        String reg = regNo.trim().toUpperCase(), mail = email.trim().toLowerCase();
        if (!reg.matches("[A-Z0-9][A-Z0-9/-]{5,19}") || !reg.matches(".*\\d.*"))
            throw new IllegalArgumentException("Admission number: 6-20 letters, digits, / or -, e.g. SCS/10001/26.");
        if (mail.length() > 100 || !mail.matches("[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}"))
            throw new IllegalArgumentException("Enter a valid email address, e.g. name@college.example.com.");
        checkNewPassword(password);
        validate(name, department, phone);              // everything is checked before anything is changed
        this.regNo = reg;
        setEmail(mail);
        setPasswordHash(hash(password));
        updateStudent(name, department, phone);
    }

    private static void validate(String name, String department, String phone) {
        String n = name.trim();
        if (n.length() < 3 || n.length() > 80) throw new IllegalArgumentException("Name needs 3-80 characters.");
        if (!n.matches("[\\p{L} .'-]+")) throw new IllegalArgumentException("Name can only contain letters, spaces and . ' -");
        if (department == null || department.isBlank()) throw new IllegalArgumentException("Choose or type your class, e.g. S3 CS2.");
        if (department.trim().length() > 40 || !department.trim().matches("[\\p{L}\\d .&'()/-]+"))
            throw new IllegalArgumentException("Class: up to 40 letters, digits and . & ' ( ) / -, e.g. S3 CS2.");
        java.util.regex.Matcher c = CLASS.matcher(department.trim());
        if (c.matches() && (Integer.parseInt(c.group(1)) < 1 || Integer.parseInt(c.group(1)) > 8))
            throw new IllegalArgumentException("Semester must be S1 to S8 (e.g. S3 CS2).");
        if (!phone.trim().matches("\\d{10}")) throw new IllegalArgumentException("Phone number must have 10 digits.");
    }

    /** PPT method: validates and updates the editable details. */
    public void updateStudent(String name, String department, String phone) {
        validate(name, department, phone);
        setName(name.trim());
        this.department = department.trim();
        this.phone = phone.trim();
    }

    /** PPT method. */
    public String displayStudent() {
        return regNo + " | " + getName() + " | " + department + " | " + getEmail() + " | " + phone;
    }

    @Override public String getRole() { return "Student"; }

    @Override public JFrame openDashboard(CollegeBusSystem system) { return new StudentDashboard(this, system); }

    @Override public String displayDetails() { return "Student " + displayStudent(); }   // POLYMORPHISM (overriding)

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", getId()); m.put("regNo", regNo); m.put("name", getName()); m.put("firstName", getFirstName());
        m.put("email", getEmail()); m.put("department", department); m.put("phone", phone);
        return m;
    }

    public String getRegNo() { return regNo; }
    public String getDepartment() { return department; }

    /** A class code: semester, department letters, division, e.g. "S3 CS2" or "s5ct1". */
    private static final java.util.regex.Pattern CLASS = java.util.regex.Pattern.compile("(?i)S(\\d{1,2})\\s*([a-z]+)\\d*");

    public String getDepartmentCode() { return departmentOf(department); }

    /** "S3 CS2" (semester 3, CS, division 2) -> "CS"; any other text (e.g. "Grade 11") is its own department. */
    public static String departmentOf(String studentClass) {
        java.util.regex.Matcher m = CLASS.matcher(studentClass == null ? "" : studentClass.trim());
        return m.matches() ? m.group(2).toUpperCase() : studentClass;
    }
    public String getPhone() { return phone; }
    public int getReservationCount() { return reservationCount; }
    public void setReservationCount(int reservationCount) { this.reservationCount = reservationCount; }
}

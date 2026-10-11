package com.collegebus.dao;

import com.collegebus.model.Student;
import com.collegebus.util.DatabaseConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** All SQL for students. */
public class StudentDAO {
    private static final String SELECT = "SELECT s.*, (SELECT COUNT(*) FROM reservations r WHERE r.student_id = s.student_id "
            + "AND r.status = 'BOOKED') AS res_count FROM students s ";

    private Student map(ResultSet rs) throws SQLException {
        Student s = new Student(rs.getInt("student_id"), rs.getString("reg_no"), rs.getString("name"), rs.getString("email"),
                rs.getString("password"), rs.getString("department"), rs.getString("phone"));
        s.setReservationCount(rs.getInt("res_count"));
        return s;
    }

    /** Students can sign in with their email or their admission number. */
    public Student findByLogin(String login) throws SQLException {
        List<Student> found = query(SELECT + "WHERE s.email = ? OR s.reg_no = ?", login.trim().toLowerCase(), login.trim().toUpperCase());
        return found.isEmpty() ? null : found.get(0);
    }

    /** Counted by MySQL - an institution can have hundreds of students. */
    public int count() throws SQLException {
        return Queries.intValue("SELECT COUNT(*) FROM students");
    }

    public List<Student> findAll() throws SQLException {
        return query(SELECT + "ORDER BY s.reg_no");
    }

    private List<Student> query(String sql, Object... params) throws SQLException {
        List<Student> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    public void insert(Student s) throws SQLException {
        try {
            Queries.update("INSERT INTO students (reg_no, name, email, password, department, phone) VALUES (?,?,?,?,?,?)",
                    s.getRegNo(), s.getName(), s.getEmail(), s.getPasswordHash(), s.getDepartment(), s.getPhone());
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new IllegalArgumentException("A student with this admission number or email already exists.");
        }
    }

    /** Demo setup only: gives every student the same password hash (the demo database, never a real one). */
    public void setAllPasswords(String passwordHash) throws SQLException {
        Queries.update("UPDATE students SET password=?", passwordHash);
    }

    public void savePassword(com.collegebus.model.User s) throws SQLException {
        Queries.update("UPDATE students SET password=? WHERE student_id=?", s.getPasswordHash(), s.getId());
    }

    public void update(Student s) throws SQLException {
        Queries.update("UPDATE students SET name=?, department=?, phone=? WHERE student_id=?",
                s.getName(), s.getDepartment(), s.getPhone(), s.getId());
    }

    /** Removes the account; ON DELETE CASCADE removes its reservations, which frees any booked seats. */
    public void delete(int studentId) throws SQLException {
        if (Queries.update("DELETE FROM students WHERE student_id = ?", studentId) == 0)
            throw new IllegalArgumentException("That student no longer exists.");
    }

    /** Booked seats on trips that have not left yet (shown before an admin removes the student). */
    public int countUpcoming(int studentId) throws SQLException {
        return Queries.intValue("SELECT COUNT(*) FROM reservations x JOIN buses b ON b.bus_id = x.bus_id WHERE x.student_id = ? "
                + "AND x.status = 'BOOKED' AND TIMESTAMP(x.travel_date, IF(x.direction = 'RETURN', b.return_departure, b.departure)) > NOW()", studentId);
    }
}

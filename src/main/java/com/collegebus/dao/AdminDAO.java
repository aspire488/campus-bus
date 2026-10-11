package com.collegebus.dao;

import com.collegebus.model.Admin;
import com.collegebus.model.Timetable;
import java.time.LocalTime;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;

/** All SQL for administrator accounts and the institution settings. */
public class AdminDAO {
    public Admin find(String username) throws SQLException {
        List<Admin> found = query("SELECT * FROM admins WHERE username = ?", username.trim().toLowerCase());
        return found.isEmpty() ? null : found.get(0);
    }

    public List<Admin> findAll() throws SQLException {
        return query("SELECT * FROM admins ORDER BY username");
    }

    private List<Admin> query(String sql, Object... params) throws SQLException {
        List<Admin> list = new ArrayList<>();
        for (Object[] r : Queries.rows(sql, params))        // columns: admin_id, username, name, password
            list.add(new Admin((Integer) r[0], (String) r[1], (String) r[2], (String) r[3]));
        return list;
    }

    public void savePassword(com.collegebus.model.User a) throws SQLException {
        Queries.update("UPDATE admins SET password=? WHERE admin_id=?", a.getPasswordHash(), a.getId());
    }

    public int count() throws SQLException {
        return Queries.intValue("SELECT COUNT(*) FROM admins");
    }

    /** First-run setup: inserts only while the table is still empty, in one statement, so it can never add a second admin. */
    public boolean insertFirst(Admin a) throws SQLException {
        return Queries.update("INSERT INTO admins (username, name, password) SELECT ?, ?, ? FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM admins)",
                a.getUsername(), a.getName(), a.getPasswordHash()) == 1;
    }

    public void insert(Admin a) throws SQLException {
        try {
            Queries.update("INSERT INTO admins (username, name, password) VALUES (?,?,?)", a.getUsername(), a.getName(), a.getPasswordHash());
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new IllegalArgumentException("The username " + a.getUsername() + " is already taken.");
        }
    }

    public boolean exists(int adminId) throws SQLException {
        return Queries.intValue("SELECT COUNT(*) FROM admins WHERE admin_id = ?", adminId) == 1;
    }

    /** Deletes an admin unless it is the last one. The admin rows stay locked while counting, so two admins removing each other at once cannot leave none. */
    public void deleteIfNotLast(int adminId) throws SQLException {
        try (java.sql.Connection c = com.collegebus.util.DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try (java.sql.PreparedStatement lock = c.prepareStatement("SELECT admin_id FROM admins FOR UPDATE");
                 java.sql.ResultSet rs = lock.executeQuery();
                 java.sql.PreparedStatement del = c.prepareStatement("DELETE FROM admins WHERE admin_id = ?")) {
                int count = 0;
                boolean found = false;
                while (rs.next()) { count++; found |= rs.getInt(1) == adminId; }
                if (!found) throw new IllegalArgumentException("That administrator no longer exists.");
                if (count <= 1) throw new IllegalStateException("At least one administrator must remain.");
                del.setInt(1, adminId);
                del.executeUpdate();
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** The saved timetable, or the default one (classes 09:00, arrive 08:30-08:45, evening 16:30). */
    public Timetable timetable() throws SQLException {
        String start = setting("class_start"), from = setting("arrive_from"), by = setting("arrive_by"), evening = setting("evening_departure");
        if (start == null || from == null || by == null || evening == null) return Timetable.DEFAULT;
        return new Timetable(LocalTime.parse(start), LocalTime.parse(from), LocalTime.parse(by), LocalTime.parse(evening));
    }

    public void saveTimetable(Timetable t) throws SQLException {
        saveSetting("class_start", t.getClassStart().toString());
        saveSetting("arrive_from", t.getArriveFrom().toString());
        saveSetting("arrive_by", t.getArriveBy().toString());
        saveSetting("evening_departure", t.getEvening().toString());
    }

    public String setting(String name) throws SQLException {
        List<Object[]> rows = Queries.rows("SELECT value FROM settings WHERE name = ?", name);
        return rows.isEmpty() ? null : (String) rows.get(0)[0];
    }

    public void saveSetting(String name, String value) throws SQLException {
        Queries.update("INSERT INTO settings (name, value) VALUES (?, ?) AS new ON DUPLICATE KEY UPDATE value = new.value", name, value);
    }
}

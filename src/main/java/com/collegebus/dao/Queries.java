package com.collegebus.dao;

import com.collegebus.util.DatabaseConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Small JDBC helpers shared by the DAOs: run a PreparedStatement with any parameters. */
final class Queries {
    private Queries() { }

    static int intValue(String sql, Object... params) throws SQLException {
        List<Object[]> rows = rows(sql, params);
        return rows.isEmpty() ? 0 : ((Number) rows.get(0)[0]).intValue();
    }

    /** Every row as an Object[] - used for counts and dashboard statistics. */
    static List<Object[]> rows(String sql, Object... params) throws SQLException {
        List<Object[]> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                int cols = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    Object[] row = new Object[cols];
                    for (int i = 0; i < cols; i++) row[i] = rs.getObject(i + 1);
                    list.add(row);
                }
            }
        }
        return list;
    }

    static int update(String sql, Object... params) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            return ps.executeUpdate();
        }
    }
}

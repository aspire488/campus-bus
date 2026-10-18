package com.collegebus;

import com.collegebus.gui.LoginFrame;
import com.collegebus.gui.Theme;
import com.collegebus.service.CollegeBusSystem;
import com.collegebus.util.DatabaseConnection;
import javax.swing.SwingUtilities;

/** College Bus Reservation System - starts the Swing desktop application at the login screen. */
public class Main {
    /** Shows why the app cannot start (on the Swing thread) and exits. */
    private static void stop(String message, String title) {
        try {
            SwingUtilities.invokeAndWait(() -> javax.swing.JOptionPane.showMessageDialog(null, message, title, javax.swing.JOptionPane.ERROR_MESSAGE));
        } catch (Exception ignored) {
            System.out.println(title + ": " + message);
        }
        System.exit(1);
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--setup-demo")) {      // used by setup-windows.cmd; no window
            try {
                com.collegebus.service.DemoSetup.run();
                System.exit(0);
            } catch (Exception e) {
                System.out.println("Demo setup failed: " + (e instanceof java.sql.SQLException ? DatabaseConnection.explain(e) : e.getMessage()));
                System.exit(1);
            }
        }
        try {
            DatabaseConnection.ensureDatabase();      // creates college_bus_db on first run
        } catch (IllegalStateException refused) {     // the database is in a state the app must not touch: say so, don't run on it
            stop(refused.getMessage(), "CAMPUS BUS - database not changed");
        } catch (java.sql.SQLException e) {
            if (DatabaseConnection.isConnectionProblem(e) || DatabaseConnection.isAccessProblem(e)) {
                System.out.println("MySQL not reachable yet: " + DatabaseConnection.explain(e));   // the login screen says what to do
            } else {                                  // reachable, but this version's changes could not be made: every screen would fail
                // state and code only: a database message can quote stored values
                System.out.println("Database upgrade failed (" + e.getSQLState() + "/" + e.getErrorCode() + ")");
                stop("The database could not be updated for this version of CAMPUS BUS, so the app did not open.\n"
                        + "No data was removed. Close any other CAMPUS BUS window or database tool, then start the app again.\n"
                        + "If this keeps happening, give this code to whoever set up the database: " + e.getSQLState() + "/" + e.getErrorCode()
                        + " (the MySQL user also needs the ALTER privilege).",
                        "CAMPUS BUS - database update failed");
            }
        } catch (Exception e) {
            System.out.println("Database check failed: " + e.getMessage());
        }
        Theme.install();
        SwingUtilities.invokeLater(() -> {
            CollegeBusSystem system = new CollegeBusSystem();
            String firstAdmin = null;
            if (LoginFrame.needsSetup(system)) {                   // new installation: setup window first, login screen after
                firstAdmin = LoginFrame.setUpInstitution(system);
                if (firstAdmin == null) System.exit(0);
            }
            LoginFrame login = new LoginFrame(system);
            if (firstAdmin != null) login.welcomeAdmin(firstAdmin);
            login.setVisible(true);
        });
    }
}

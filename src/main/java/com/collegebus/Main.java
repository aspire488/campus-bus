package com.collegebus;

import com.collegebus.gui.LoginFrame;
import com.collegebus.gui.Theme;
import com.collegebus.service.CollegeBusSystem;
import com.collegebus.util.DatabaseConnection;
import javax.swing.SwingUtilities;

/** College Bus Reservation System - starts the Swing desktop application at the login screen. */
public class Main {
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
        } catch (Exception e) {
            System.out.println("MySQL not reachable yet: " + e.getMessage());
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

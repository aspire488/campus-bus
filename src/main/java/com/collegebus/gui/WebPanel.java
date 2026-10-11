package com.collegebus.gui;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

/**
 * Swing component that hosts the HTML/CSS/JS screens (resources/web) inside the desktop window.
 * JFXPanel lets a JavaFX WebView live in a Swing layout; the Java bridge object is exposed to JavaScript as "app".
 */
public class WebPanel extends JFXPanel {
    private final WebBridge bridge;        // strong reference - WebView only keeps a weak one
    private WebEngine engine;
    private String page;
    private boolean ready;

    public WebPanel(String role, WebBridge bridge, String firstPage) {
        this.bridge = bridge;
        this.page = firstPage;
        bridge.setWebPanel(this);
        Platform.setImplicitExit(false);   // keep JavaFX alive after logout so the next login can reuse it
        Platform.runLater(() -> {
            WebView view = new WebView();
            view.setContextMenuEnabled(false);
            engine = view.getEngine();
            engine.setOnAlert(e -> bridge.notice(e.getData()));       // JavaScript alert() -> Swing toast
            engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
                if (state == Worker.State.SUCCEEDED) {
                    JSObject window = (JSObject) engine.executeScript("window");
                    window.setMember("app", this.bridge);
                    ready = true;
                    engine.executeScript("App.boot('" + role + "', '" + page + "')");
                }
            });
            engine.load(getClass().getResource("/web/index.html").toExternalForm());
            setScene(new Scene(view));
        });
    }

    /** Swing -> JavaScript: switch the web page (called from the route-line menu). */
    public void show(String newPage) {
        show(newPage, "{}");
    }

    /** Same, with page parameters as a JSON object made by Json.of (e.g. which bus and date the seat map shows). */
    public void show(String newPage, String paramsJson) {
        Platform.runLater(() -> {
            page = newPage;
            if (ready) engine.executeScript("App.go('" + newPage + "', " + paramsJson + ")");
        });
    }

    /** Prints the current page (the travel pass) using the system print dialog. */
    String print() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) return "No printer is set up on this computer.";
        if (job.showPrintDialog(null)) {
            engine.print(job);
            job.endJob();
        }
        return "";
    }
}

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
    private static final String INDEX = WebPanel.class.getResource("/web/index.html").toExternalForm();
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
                // the Java bridge only for the app's own page, never for anything else the view might end up showing
                if (state == Worker.State.SUCCEEDED && !ownPage(engine.getLocation()) && !"about:blank".equals(engine.getLocation()))
                    System.out.println("Bridge withheld from " + engine.getLocation());     // (about:blank = the page closed at logout)
                if (state == Worker.State.SUCCEEDED && ownPage(engine.getLocation())) {
                    JSObject window = (JSObject) engine.executeScript("window");
                    window.setMember("app", this.bridge);
                    ready = true;
                    engine.executeScript("App.boot('" + role + "', '" + page + "')");
                }
            });
            engine.load(INDEX);
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

    static boolean ownPage(String location) { return sameUrl(location, INDEX); }

    /**
     * The same address however it is spelled: WebKit writes "file:///C:/Jos%C3%A9%20x/..." where Java writes
     * "file:/C:/José x/..." (or "%20" for the space). Both are percent-decoded and the file: prefix normalised.
     */
    public static boolean sameUrl(String a, String b) {
        return a != null && b != null && plain(a).equals(plain(b));
    }

    private static String plain(String url) {
        String u = java.net.URLDecoder.decode(url.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
        return u.replace("file:///", "file:/");
    }

    /** After logout: stops the page (and its 30-second live refresh) so a closed window never calls the database again. */
    public void close() {
        Platform.runLater(() -> {
            ready = false;
            if (engine != null) engine.load("about:blank");
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

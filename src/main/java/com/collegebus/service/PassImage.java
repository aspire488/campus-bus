package com.collegebus.service;

import com.collegebus.model.Reservation;
import com.collegebus.util.Json;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.transform.Transform;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import netscape.javascript.JSObject;

/**
 * The in-app pass as a picture: the student pages' own "Your pass" card (resources/web, App.pages.pass), drawn off-screen
 * from the same reservation data the live page receives, and captured at twice the screen resolution. The PDF pass is
 * made from this picture, so the saved/emailed pass always looks exactly like the pass in the app.
 */
public final class PassImage {
    private PassImage() { }

    private static final String INDEX = PassImage.class.getResource("/web/index.html").toExternalForm();
    private static final double SCALE = 2;
    private static final int WIDTH = 1000;
    /** Strong references while a pass is drawn: an off-screen WebView (and the weakly held JS bridge) could be garbage-collected mid-load. */
    private static final Set<Object> DRAWING = ConcurrentHashMap.newKeySet();
    /** Every face the pass card uses (css/style.css): the picture is taken only once all of them are loaded. */
    private static final String FONTS_READY = "Promise.all(['400 16px Overpass', '600 16px Overpass', '800 16px Overpass', '700 16px OverpassMono']"
            + ".map(function (f) { return document.fonts.load(f); })).then(function (all) {"
            + " window.passFonts = all.every(function (faces) { return faces.length > 0; }) ? 'ok' : 'missing'; },"
            + " function () { window.passFonts = 'missing'; });";

    /** The data the pass page shows for one reservation - also what WebBridge sends to the live page. */
    public static Map<String, Object> data(Reservation r, String institution, String arrivalWindow) {
        Map<String, Object> m = new LinkedHashMap<>(r.toMap());
        m.put("stops", r.getBus().stopTimes());
        m.put("live", r.getBus().estimatePosition(r.getTravelDate(), LocalDateTime.now()));
        m.put("institution", institution);
        m.put("window", arrivalWindow);
        return m;
    }

    /** What the page's JavaScript may call while drawing the pass: this one reservation's data, nothing else. */
    public static final class Bridge {
        private final String reply;
        Bridge(String reply) { this.reply = reply; }
        public String reservation(String id) { return reply; }
        public void log(String message) { System.out.println("[pass] " + message); }
        public void navigated(String item) { }
        public String print() { return ""; }
    }

    /** Draws the pass for {@code data} (see data()) and returns it as an image. Must not be called on the JavaFX thread. */
    public static BufferedImage render(Map<String, Object> data) throws IOException {
        if (Platform.isFxApplicationThread()) throw new IllegalStateException("The pass is drawn on the JavaFX thread: call this from a background thread.");
        startJavaFx();
        Bridge bridge = new Bridge("{\"ok\":true,\"data\":" + Json.of(data) + "}");
        CompletableFuture<BufferedImage> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                WebView view = new WebView();
                view.setContextMenuEnabled(false);
                Scene scene = new Scene(view, WIDTH, 900);
                // a real window, far off-screen and without a taskbar entry: without one the page is never sized or laid out
                Stage stage = new Stage(StageStyle.UTILITY);
                stage.setScene(scene);
                stage.setX(-30000);
                stage.setY(-30000);
                WebEngine engine = view.getEngine();
                List<Object> held = List.of(view, scene, stage, engine, bridge);
                DRAWING.addAll(held);
                done.whenComplete((img, err) -> {                       // whatever happens: window closed, memory released
                    DRAWING.removeAll(held);
                    Platform.runLater(() -> { engine.load("about:blank"); stage.close(); });
                });
                engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
                    if (done.isDone()) return;
                    if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) {
                        done.completeExceptionally(new IOException("The pass page could not be loaded (" + state + ")."));
                        return;
                    }
                    if (state != Worker.State.SUCCEEDED) return;
                    try {
                        ((JSObject) engine.executeScript("window")).setMember("app", bridge);
                        engine.executeScript("var s = document.createElement('style');"
                                + "s.textContent = '*, *::before, *::after { animation: none !important; transition: none !important; }';"
                                + "document.head.appendChild(s); App.role = 'student'; App.pages.pass({id: 0}); clearInterval(App.timer);" + FONTS_READY);
                        settle(view, engine, stage, done);
                    } catch (Throwable t) {                                  // a listener's exception would otherwise vanish
                        done.completeExceptionally(t);
                    }
                });
                stage.show();
                engine.load(INDEX);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        try {
            return done.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            done.completeExceptionally(e);                                  // a timeout still closes the window
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IOException("The pass could not be drawn: " + cause.getMessage(), cause);
        }
    }

    /** Waits (on a plain thread: off-screen there are no animation pulses) for the fonts, grows the window to the whole card, captures it. */
    private static void settle(WebView view, WebEngine engine, Stage stage, CompletableFuture<BufferedImage> done) {
        Thread t = new Thread(() -> {
            try {
                String fonts = null;
                for (int i = 0; i < 100 && fonts == null && !done.isDone(); i++) {
                    Thread.sleep(100);
                    fonts = onFx(() -> (String) engine.executeScript("window.passFonts || null"));
                }
                if (!"ok".equals(fonts)) throw new IOException("The pass fonts did not load (" + fonts + ").");
                // only the card has to fit, not the whole page: Windows may cap the window at the screen size (small or scaled laptops)
                // everything below the card goes first (live position, email status, buttons): it changes between drawings,
                // and a different page height would scroll the card to a slightly different place - the same booking must
                // always give the same picture
                String toTop = "(function () { var p = document.querySelector('.pass');"
                        + " while (p.nextSibling) p.parentNode.removeChild(p.nextSibling);"
                        + " p.scrollIntoView({block: 'start'}); return Math.ceil(p.getBoundingClientRect().height); })()";
                int card = onFx(() -> ((Number) engine.executeScript(toTop)).intValue());
                if (card + 40 > view.getHeight()) {
                    onFx(() -> { stage.setHeight(card + 160); return null; });
                    Thread.sleep(300);
                    onFx(() -> engine.executeScript(toTop));
                }
                Thread.sleep(200);                                          // last layout pass
                done.complete(onFx(() -> capture(view, engine)));
            } catch (Throwable e) {
                done.completeExceptionally(e);
            }
        }, "pass-settle");
        t.setDaemon(true);
        t.start();
    }

    private interface FxWork<T> { T run() throws Exception; }

    private static <T> T onFx(FxWork<T> work) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> { try { f.complete(work.run()); } catch (Throwable t) { f.completeExceptionally(t); } });
        return f.get(5, TimeUnit.SECONDS);
    }

    /** The .pass card only (not the page around it), at SCALE times its size on screen; refuses to return a clipped card. */
    private static BufferedImage capture(WebView view, WebEngine engine) throws IOException {
        Object box = engine.executeScript("(function () { var p = document.querySelector('.pass'); if (!p) return '';"
                + "var b = p.getBoundingClientRect(); return b.left + ',' + b.top + ',' + b.width + ',' + b.height; })()");
        if (!(box instanceof String s) || s.isEmpty()) throw new IOException("The pass page showed no pass.");
        String[] v = s.split(",");
        double x = Double.parseDouble(v[0]), y = Double.parseDouble(v[1]), w = Double.parseDouble(v[2]), h = Double.parseDouble(v[3]);
        SnapshotParameters p = new SnapshotParameters();
        p.setTransform(Transform.scale(SCALE, SCALE));
        BufferedImage page = SwingFXUtils.fromFXImage(view.snapshot(p, null), null);
        int cx = (int) Math.round(x * SCALE), cy = (int) Math.round(y * SCALE), cw = (int) Math.round(w * SCALE), ch = (int) Math.round(h * SCALE);
        if (cw <= 0 || ch <= 0 || cx < 0 || cy < 0 || cx + cw > page.getWidth() || cy + ch > page.getHeight())
            throw new IOException("The pass did not fit the drawing area (pass " + s + ", area " + page.getWidth() + "x" + page.getHeight() + ").");
        return page.getSubimage(cx, cy, cw, ch);
    }

    /** The JavaFX runtime is already running inside the app; tests and tools start it here. */
    private static void startJavaFx() throws IOException {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // the app's windows started it
        } catch (UnsupportedOperationException noDisplay) {
            throw new IOException("The pass needs a desktop session to be drawn.", noDisplay);
        }
        Platform.setImplicitExit(false);
    }
}

package com.homelab.dimmer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public class DimService extends Service {
    private static final int PORT = 8765;
    private static DimService running;
    private WindowManager wm;
    private final java.util.List<View> overlays = new java.util.ArrayList<>();
    private int mode = 0;
    private Thread server;
    private final Handler main = new Handler(Looper.getMainLooper());

    static DimService current() { return running; }

    @Override public void onCreate() {
        super.onCreate();
        running = this;
        startAsForeground();
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        startServer();
    }

    private void startAsForeground() {
        android.app.Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            android.app.NotificationChannel ch = new android.app.NotificationChannel("dimmer", "Dimmer", android.app.NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(ch);
            nb = new android.app.Notification.Builder(this, "dimmer");
        } else {
            nb = new android.app.Notification.Builder(this);
        }
        nb.setContentTitle("Dimmer running")
          .setContentText("Screen dim control is on")
          .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
          .setOngoing(true);
        startForeground(1, nb.build());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        apply();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        running = null;
        if (server != null) server.interrupt();
        removeOverlays();
        super.onDestroy();
    }

    /** Set the dim level (0 = off, MAX_DIM = darkest) on the main thread. */
    void setLevel(final int v) {
        Prefs.setLevel(this, v);
        main.post(new Runnable() { public void run() { apply(); } });
    }

    int level() { return Prefs.level(this); }

    private boolean canDraw() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || Settings.canDrawOverlays(this);
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
    }

    private WindowManager.LayoutParams params(int x, int y, int w, int h) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = x;
        lp.y = y;
        return lp;
    }

    private void addView(View v, WindowManager.LayoutParams lp) {
        v.setBackgroundColor(0xFF000000);
        try { wm.addView(v, lp); overlays.add(v); } catch (RuntimeException ignored) { }
    }

    /** Create or update the dim layer. Called on the main thread. */
    void apply() {
        int v = Prefs.level(this);
        if (v <= 0) { removeOverlays(); mode = 0; return; }
        if (!canDraw()) return;
        int want = MainActivity.open ? 2 : 1;
        if (want != mode) {
            removeOverlays();
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int W = dm.widthPixels, H = dm.heightPixels;
            if (want == 1) {
                addView(new View(this), params(0, 0, W, H));
            } else {
                // Leave a centred box free for the control panel (60% wide, 70% tall).
                int bw = (int) (W * 0.6), bh = (int) (H * 0.7);
                int x0 = (W - bw) / 2, y0 = (H - bh) / 2;
                addView(new View(this), params(0, 0, W, y0));                 // top
                addView(new View(this), params(0, y0 + bh, W, H - (y0 + bh))); // bottom
                addView(new View(this), params(0, y0, x0, bh));               // left
                addView(new View(this), params(x0 + bw, y0, W - (x0 + bw), bh)); // right
            }
            mode = want;
        }
        for (View o : overlays) o.setAlpha(v / 100f);
    }

    private void removeOverlays() {
        for (View o : overlays) {
            try { wm.removeView(o); } catch (RuntimeException ignored) { }
        }
        overlays.clear();
    }

    private void startServer() {
        server = new Thread(new Runnable() {
            public void run() {
                try (java.net.ServerSocket ss = new java.net.ServerSocket(PORT)) {
                    while (!Thread.currentThread().isInterrupted()) {
                        try (java.net.Socket s = ss.accept()) {
                            handle(s);
                        } catch (java.io.IOException ignored) { }
                    }
                } catch (java.io.IOException e) {
                    // port in use or network down; the service keeps running without the server
                }
            }
        }, "dimmer-http");
        server.setDaemon(true);
        server.start();
    }

    private void handle(java.net.Socket s) throws java.io.IOException {
        s.setSoTimeout(5000);
        java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(s.getInputStream()));
        String line = in.readLine();
        if (line == null) return;
        String[] parts = line.split(" ");
        String path = parts.length > 1 ? parts[1] : "/";
        String query = "";
        int q = path.indexOf('?');
        if (q >= 0) { query = path.substring(q + 1); path = path.substring(0, q); }
        java.util.Map<String, String> params = new java.util.HashMap<>();
        for (String kv : query.split("&")) {
            int eq = kv.indexOf('=');
            if (eq > 0) params.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        String body;
        int code = 200;
        if (!Prefs.token(this).equals(params.get("t"))) {
            code = 403;
            body = "{\"error\":\"bad token\"}";
        } else {
            int cur = level();
            switch (path) {
                case "/up": setLevel(Math.min(Prefs.MAX_DIM, cur + 10)); break;
                case "/down": setLevel(Math.max(0, cur - 10)); break;
                case "/off": setLevel(0); break;
                case "/set":
                    try { setLevel(Integer.parseInt(params.get("v"))); }
                    catch (Exception e) { code = 400; }
                    break;
                default: break;
            }
            body = "{\"level\":" + level() + ",\"max\":" + Prefs.MAX_DIM + "}";
        }
        String status = code == 200 ? "200 OK" : (code == 403 ? "403 Forbidden" : "400 Bad Request");
        byte[] bytes = body.getBytes("UTF-8");
        java.io.OutputStream out = s.getOutputStream();
        out.write(("HTTP/1.1 " + status + "\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
        out.write(bytes);
        out.flush();
    }
}

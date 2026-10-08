package com.tvlink.receiver;

import android.app.*;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import java.io.*;
import java.net.*;
import java.util.*;
import dadb.AdbKeyPair;
import dadb.AdbShellResponse;
import dadb.Dadb;

public class ReceiverService extends Service {
    static volatile String code = "";
    static volatile String server = "starting";
    static volatile String adb = "not tried yet";
    static volatile String last = "-";
    static final Map<String, Integer> KEYS = new HashMap<>();
    static {
        KEYS.put("HOME", 3); KEYS.put("BACK", 4);
        KEYS.put("UP", 19); KEYS.put("DOWN", 20);
        KEYS.put("LEFT", 21); KEYS.put("RIGHT", 22); KEYS.put("OK", 23);
        KEYS.put("VOL_UP", 24); KEYS.put("VOL_DOWN", 25);
        KEYS.put("POWER", 26); KEYS.put("MENU", 82); KEYS.put("MUTE", 164);
        KEYS.put("CH_UP", 166); KEYS.put("CH_DOWN", 167);
        KEYS.put("PLAY_PAUSE", 85); KEYS.put("REWIND", 89); KEYS.put("FF", 90);
        for (int i = 0; i <= 9; i++) KEYS.put("NUM" + i, 7 + i);
    }
    private Dadb dadb;
    private boolean running = false;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("tvlink", "TVLink", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
            nb = new Notification.Builder(this, "tvlink");
        } else {
            nb = new Notification.Builder(this);
        }
        startForeground(1, nb.setContentTitle("TVLink Receiver running")
            .setSmallIcon(android.R.drawable.ic_dialog_info).build());
        if (!running) {
            running = true;
            new Thread(this::serve).start();
        }
        return START_STICKY;
    }

    private void serve() {
        try {
            ServerSocket ss = new ServerSocket(8765);
            server = "listening on port 8765";
            while (true) {
                final Socket s = ss.accept();
                new Thread(() -> handle(s)).start();
            }
        } catch (Exception e) {
            server = "FAILED: " + e.getMessage();
            running = false;
        }
    }

    private synchronized Dadb adb() throws Exception {
        if (dadb != null) return dadb;
        File priv = new File(getFilesDir(), "adbkey");
        File pub = new File(getFilesDir(), "adbkey.pub");
        if (!priv.exists()) AdbKeyPair.generate(priv, pub);
        dadb = Dadb.create("127.0.0.1", 5555, AdbKeyPair.read(priv, pub));
        return dadb;
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String line = in.readLine();
            String body;
            if (line == null) { s.close(); return; }
            String[] parts = line.split(" ");
            String path = parts.length > 1 ? parts[1] : "/";
            String q = path.contains("?") ? path.substring(path.indexOf('?') + 1) : "";
            Map<String, String> p = new HashMap<>();
            for (String kv : q.split("&")) {
                String[] a = kv.split("=", 2);
                if (a.length == 2) p.put(a[0], a[1]);
            }
            if (!code.equals(p.get("code"))) {
                body = "{\"ok\":false,\"error\":\"wrong pairing code\"}";
            } else if (path.startsWith("/ping")) {
                body = "{\"ok\":true,\"receiver\":\"TVLink Receiver\"}";
            } else if (path.startsWith("/cmd")) {
                String key = p.get("key");
                Integer kc = key == null ? null : KEYS.get(key);
                if (kc == null) {
                    body = "{\"ok\":false,\"error\":\"unsupported key\"}";
                } else {
                    try {
                        AdbShellResponse r = adb().shell("input keyevent " + kc);
                        boolean ok = r.getExitCode() == 0;
                        adb = ok ? "connected, working" : "shell exit " + r.getExitCode();
                        last = key + (ok ? " OK" : " FAILED");
                        body = "{\"ok\":" + ok + ",\"key\":\"" + key + "\"}";
                    } catch (Exception e) {
                        dadb = null;
                        adb = "FAILED: " + e.getMessage();
                        last = key + " FAILED";
                        body = "{\"ok\":false,\"error\":\"adb failed\"}";
                    }
                }
            } else {
                body = "{\"ok\":false,\"error\":\"unknown path\"}";
            }
            byte[] b = body.getBytes("UTF-8");
            OutputStream out = s.getOutputStream();
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
                + "Access-Control-Allow-Origin: *\r\nContent-Length: " + b.length
                + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
            out.write(b);
            out.flush();
            s.close();
        } catch (Exception e) {
            try { s.close(); } catch (Exception x) { }
        }
    }
}

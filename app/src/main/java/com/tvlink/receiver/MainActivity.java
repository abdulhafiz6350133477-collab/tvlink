package com.tvlink.receiver;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.widget.TextView;
import java.net.*;
import java.util.*;

public class MainActivity extends Activity {
    TextView t;
    Handler h = new Handler();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (ReceiverService.code.isEmpty()) {
            ReceiverService.code = String.format("%06d", new Random().nextInt(1000000));
        }
        t = new TextView(this);
        t.setTextSize(30);
        t.setGravity(Gravity.CENTER);
        setContentView(t);
        Intent i = new Intent(this, ReceiverService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        refresh.run();
    }

    Runnable refresh = new Runnable() {
        public void run() {
            t.setText("TVLink Receiver\n\nPairing code: " + ReceiverService.code
                + "\nTV IP: " + ip() + "\n\nServer: " + ReceiverService.server
                + "\nADB: " + ReceiverService.adb
                + "\nLast command: " + ReceiverService.last);
            h.postDelayed(this, 1000);
        }
    };

    String ip() {
        try {
            for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress a : Collections.list(n.getInetAddresses())) {
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address) return a.getHostAddress();
                }
            }
        } catch (Exception e) { }
        return "unknown";
    }
}

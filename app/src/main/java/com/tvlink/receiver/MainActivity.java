package com.tvlink.receiver;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;
import java.util.Random;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String code = String.format("%06d", new Random().nextInt(1000000));
        TextView t = new TextView(this);
        t.setText("TVLink Receiver\n\nPairing code: " + code
            + "\n\nStatus: server not started yet");
        t.setTextSize(36);
        t.setGravity(Gravity.CENTER);
        setContentView(t);
    }
}

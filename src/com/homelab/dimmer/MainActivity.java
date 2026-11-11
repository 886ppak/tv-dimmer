package com.homelab.dimmer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    /** True while this screen is in front: the dim layer is hidden so the controls stay readable. */
    static volatile boolean open = false;
    private TextView status;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        getWindow().setLayout((int) (dm.widthPixels * 0.6), (int) (dm.heightPixels * 0.7));
        getWindow().setDimAmount(0f);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#101418")));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#101418"));
        root.setPadding(60, 60, 60, 60);
        status = new TextView(this);
        status.setTextSize(26);
        status.setTextColor(Color.WHITE);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 0, 0, 30);
        root.addView(status);
        root.addView(button("Dim +  (darker)", 10));
        root.addView(button("Dim -  (lighter)", -10));
        root.addView(button("Dim off", -1000));
        root.addView(button("Dim max (darkest)", 1000));
        setContentView(root);
    }

    private final android.os.Handler ticker = new android.os.Handler();
    private final Runnable tick = new Runnable() {
        public void run() { refresh(); ticker.postDelayed(this, 1000); }
    };

    private void ensureService() {
        try { startService(new Intent(this, DimService.class)); } catch (RuntimeException ignored) { }
    }

    @Override protected void onResume() {
        super.onResume();
        open = true;
        ensureService();
        refresh();
        ticker.postDelayed(tick, 1000);
    }

    @Override protected void onPause() {
        ticker.removeCallbacks(tick);
        open = false;
        ensureService();
        super.onPause();
    }

    private Button button(String label, final int step) {
        Button bt = new Button(this);
        bt.setText(label);
        bt.setTextSize(24);
        bt.setAllCaps(false);
        bt.setPadding(40, 24, 40, 24);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.parseColor("#FFC857"));
        focused.setCornerRadius(12);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(Color.parseColor("#3A424B"));
        normal.setCornerRadius(12);
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[] { android.R.attr.state_focused }, focused);
        sld.addState(new int[] { android.R.attr.state_pressed }, focused);
        sld.addState(new int[] {}, normal);
        bt.setBackground(sld);
        bt.setTextColor(Color.parseColor("#E8ECEF"));
        bt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                ((Button) v).setTextColor(hasFocus ? Color.BLACK : Color.parseColor("#E8ECEF"));
                v.setScaleX(hasFocus ? 1.04f : 1f);
                v.setScaleY(hasFocus ? 1.04f : 1f);
            }
        });
        bt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int cur = Prefs.level(MainActivity.this);
                int next = step == -1000 ? 0 : step == 1000 ? Prefs.MAX_DIM : cur + step;
                Prefs.setLevel(MainActivity.this, next);
                ensureService();
                refresh();
            }
        });
        return bt;
    }

    private void refresh() {
        boolean ok = android.os.Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        int lvl = Prefs.level(this);
        String word = lvl == 0 ? "OFF" : lvl >= Prefs.MAX_DIM ? "MAX" : "";
        status.setText("Screen dim: " + lvl + " of " + Prefs.MAX_DIM + (word.isEmpty() ? "" : "  (" + word + ")")
            + (ok ? "" : "\nOverlay permission is OFF: allow it in settings")
            + "\nToken: " + Prefs.token(this));
    }
}

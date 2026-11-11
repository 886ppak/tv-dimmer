package com.homelab.dimmer;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.SecureRandom;

final class Prefs {
    static final String NAME = "dimmer";
    static final int MAX_DIM = 90;

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static int level(Context c) {
        return Math.max(0, Math.min(MAX_DIM, get(c).getInt("level", 0)));
    }

    static void setLevel(Context c, int v) {
        get(c).edit().putInt("level", Math.max(0, Math.min(MAX_DIM, v))).apply();
    }

    static String token(Context c) {
        SharedPreferences p = get(c);
        String t = p.getString("token", null);
        if (t == null) {
            byte[] b = new byte[12];
            new SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            t = sb.toString();
            p.edit().putString("token", t).apply();
        }
        return t;
    }
}

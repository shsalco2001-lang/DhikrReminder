package com.dhikr.reminder;

import android.content.Context;
import android.content.SharedPreferences;

/** Saved settings. */
public final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static int amount(Context c) { return sp(c).getInt("amount", 1); }
    public static int unitSeconds(Context c) { return sp(c).getInt("unit", 60); }
    public static boolean random(Context c) { return sp(c).getBoolean("random", true); }
    public static int ratePercent(Context c) { return sp(c).getInt("rate", 85); }

    public static String voice(Context c) { return sp(c).getString("voice", ""); }
    public static void saveVoice(Context c, String v) { sp(c).edit().putString("voice", v).apply(); }

    /** Apply the saved voice (if still installed) to a TTS engine. */
    public static void applyVoice(Context c, android.speech.tts.TextToSpeech tts) {
        String name = voice(c);
        if (name.isEmpty()) return;
        try {
            for (android.speech.tts.Voice v : tts.getVoices()) {
                if (v.getName().equals(name)) { tts.setVoice(v); return; }
            }
        } catch (Exception ignored) {}
    }

    public static boolean[] enabled(Context c) {
        boolean[] on = new boolean[Adhkar.LIST.length];
        String s = sp(c).getString("enabled", "");
        for (int i = 0; i < on.length; i++) {
            on[i] = s.length() != on.length || s.charAt(i) == '1';
        }
        return on;
    }

    public static void save(Context c, int amount, int unit, boolean random, int rate, boolean[] on) {
        StringBuilder b = new StringBuilder();
        for (boolean x : on) b.append(x ? '1' : '0');
        sp(c).edit()
            .putInt("amount", Math.max(1, amount))
            .putInt("unit", unit)
            .putBoolean("random", random)
            .putInt("rate", rate)
            .putString("enabled", b.toString())
            .apply();
    }

    /** Interval in milliseconds, never shorter than 5 seconds. */
    public static long intervalMs(Context c) {
        long sec = (long) amount(c) * unitSeconds(c);
        return Math.max(5, sec) * 1000L;
    }
}

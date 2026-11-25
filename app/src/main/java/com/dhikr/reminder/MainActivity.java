package com.dhikr.reminder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final int TEAL = 0xFF0F6B5C;
    private static final int GOLD = 0xFFA87A1C;
    private static final int MUTED = 0xFF5D6B66;

    private EditText amount;
    private Spinner unit;
    private Switch random;
    private SeekBar rate;
    private TextView rateLabel;
    private Button startStop;
    private CheckBox[] boxes;
    private TextToSpeech testTts;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("مذكّر الأذكار");

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFFBFAF6);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(18), dp(16), dp(18), dp(32));
        scroll.addView(root);

        TextView hint = text("التليفون هينطق الذكر بصوت عالي كل فترة، حتى والشاشة مقفولة — اسمع وردّد.", 15, MUTED, false);
        root.addView(hint);

        // ---- Interval ----
        root.addView(header("ذكّرني كل"));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        amount = new EditText(this);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        amount.setTextSize(20);
        amount.setMinWidth(dp(90));
        amount.setGravity(Gravity.CENTER);
        amount.setText(String.valueOf(Prefs.amount(this)));
        row.addView(amount);
        unit = new Spinner(this);
        ArrayAdapter<String> ua = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, new String[]{"ثانية", "دقيقة"});
        unit.setAdapter(ua);
        unit.setSelection(Prefs.unitSeconds(this) == 60 ? 1 : 0);
        row.addView(unit);
        root.addView(row);

        // quick presets
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        int[][] p = {{30, 0}, {1, 1}, {5, 1}, {10, 1}, {30, 1}};
        String[] pl = {"٣٠ ث", "١ د", "٥ د", "١٠ د", "٣٠ د"};
        for (int i = 0; i < p.length; i++) {
            final int[] v = p[i];
            Button b = smallButton(pl[i]);
            b.setOnClickListener(x -> { amount.setText(String.valueOf(v[0])); unit.setSelection(v[1]); });
            presets.addView(b);
        }
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
        hs.addView(presets);
        root.addView(hs);

        // ---- Order ----
        random = new Switch(this);
        random.setText("ترتيب عشوائي للأذكار");
        random.setTextSize(16);
        random.setChecked(Prefs.random(this));
        random.setPadding(0, dp(14), 0, dp(6));
        root.addView(random);

        // ---- Speech rate ----
        rateLabel = text("", 16, Color.BLACK, false);
        rateLabel.setPadding(0, dp(10), 0, 0);
        root.addView(rateLabel);
        rate = new SeekBar(this);
        rate.setMax(100); // 50% .. 150%
        rate.setProgress(Prefs.ratePercent(this) - 50);
        updateRateLabel();
        rate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean u) { updateRateLabel(); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(rate);

        // ---- Buttons ----
        startStop = bigButton("", TEAL);
        startStop.setOnClickListener(v -> toggle());
        root.addView(startStop);

        Button test = bigButton("جرّب الصوت", GOLD);
        test.setOnClickListener(v -> testVoice());
        root.addView(test);

        Button battery = smallButton("السماح بالعمل في الخلفية (البطارية)");
        battery.setOnClickListener(v -> {
            try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
            catch (Exception e) { toast("افتح إعدادات البطارية يدويًا"); }
        });
        root.addView(battery);

        Button voice = smallButton("إعدادات الصوت العربي (تحويل النص إلى كلام)");
        voice.setOnClickListener(v -> {
            try { startActivity(new Intent("com.android.settings.TTS_SETTINGS")); }
            catch (Exception e) { toast("افتح الإعدادات ← تحويل النص إلى كلام"); }
        });
        root.addView(voice);

        // ---- Adhkar list ----
        root.addView(header("الأذكار"));
        boolean[] on = Prefs.enabled(this);
        boxes = new CheckBox[Adhkar.LIST.length];
        for (int i = 0; i < Adhkar.LIST.length; i++) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(14));
            bg.setStroke(dp(1), 0xFFE4E2D8);
            card.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            card.setLayoutParams(lp);

            CheckBox cb = new CheckBox(this);
            cb.setText(Adhkar.LIST[i][0]);
            cb.setTextSize(19);
            cb.setTypeface(Typeface.DEFAULT_BOLD);
            cb.setChecked(on[i]);
            boxes[i] = cb;
            card.addView(cb);
            TextView f = text(Adhkar.LIST[i][1] + " — " + Adhkar.LIST[i][2], 13, MUTED, false);
            card.addView(f);
            root.addView(card);
        }

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshButton();
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    @Override
    protected void onDestroy() {
        if (testTts != null) testTts.shutdown();
        super.onDestroy();
    }

    private void save() {
        int a;
        try { a = Integer.parseInt(amount.getText().toString().trim()); } catch (Exception e) { a = 1; }
        boolean[] on = new boolean[boxes.length];
        for (int i = 0; i < boxes.length; i++) on[i] = boxes[i].isChecked();
        Prefs.save(this, a, unit.getSelectedItemPosition() == 1 ? 60 : 1,
            random.isChecked(), rate.getProgress() + 50, on);
    }

    private void toggle() {
        save();
        if (DhikrService.running) {
            stopService(new Intent(this, DhikrService.class));
            DhikrService.running = false;
        } else {
            boolean any = false;
            for (CheckBox b : boxes) any |= b.isChecked();
            if (!any) { toast("اختار ذكر واحد على الأقل"); return; }
            if (Prefs.amount(this) * Prefs.unitSeconds(this) < 5) {
                toast("أقل مدة ٥ ثواني");
            }
            Intent i = new Intent(this, DhikrService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
            DhikrService.running = true;
            toast("بدأ التذكير — تقدر تقفل الشاشة");
        }
        refreshButton();
    }

    private void refreshButton() {
        startStop.setText(DhikrService.running ? "إيقاف التذكير" : "ابدأ التذكير");
        GradientDrawable bg = (GradientDrawable) startStop.getBackground();
        bg.setColor(DhikrService.running ? 0xFFB3261E : TEAL);
    }

    private void testVoice() {
        save();
        final String say = Adhkar.spoken(0);
        if (testTts == null) {
            testTts = new TextToSpeech(this, status -> {
                if (status != TextToSpeech.SUCCESS) { toast("محرك النطق مش شغال"); return; }
                int r = testTts.setLanguage(new Locale("ar", "SA"));
                if (r < 0) r = testTts.setLanguage(new Locale("ar"));
                if (r < 0) { toast("مفيش صوت عربي — نزّله من إعدادات الصوت"); return; }
                testTts.setSpeechRate(Prefs.ratePercent(this) / 100f);
                testTts.speak(say, TextToSpeech.QUEUE_FLUSH, null, "test");
            });
        } else {
            testTts.setSpeechRate(Prefs.ratePercent(this) / 100f);
            testTts.speak(say, TextToSpeech.QUEUE_FLUSH, null, "test");
        }
    }

    private void updateRateLabel() {
        rateLabel.setText("سرعة النطق: " + (rate.getProgress() + 50) + "%");
    }

    // ---- small UI helpers ----
    private TextView text(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView header(String s) {
        TextView t = text(s, 20, TEAL, true);
        t.setPadding(0, dp(18), 0, dp(4));
        return t;
    }

    private Button bigButton(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(18);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    private Button smallButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(14);
        b.setAllCaps(false);
        return b;
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}

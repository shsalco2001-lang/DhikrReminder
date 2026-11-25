package com.dhikr.reminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Foreground service: speaks a dhikr aloud every interval, also with the screen off.
 */
public class DhikrService extends Service implements TextToSpeech.OnInitListener {

    public static final String ACTION_STOP = "com.dhikr.reminder.STOP";
    public static final String ACTION_NOW = "com.dhikr.reminder.NOW";
    private static final String CHANNEL = "dhikr_running";
    private static final int NOTIF_ID = 1;

    public static volatile boolean running = false;

    private Handler handler;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean startPending = false;
    private PowerManager.WakeLock wakeLock;
    private final Random rnd = new Random();
    private int last = -1;
    private int seq = -1;
    private String lastText = "بدأ التذكير";

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            sayNext();
            handler.postDelayed(this, Prefs.intervalMs(DhikrService.this));
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createChannel();
        tts = new TextToSpeech(this, this);
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dhikr:reminder");
        wakeLock.setReferenceCounted(false);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        goForeground();

        if (ACTION_NOW.equals(action) && running) {
            sayNext();
            return START_STICKY;
        }

        running = true;
        if (!wakeLock.isHeld()) wakeLock.acquire();
        handler.removeCallbacks(tick);
        if (ttsReady) {
            handler.postDelayed(tick, 400);
        } else {
            startPending = true;
        }
        return START_STICKY;
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            lastText = "محرك النطق مش شغال على التليفون";
            updateNotification();
            return;
        }
        int r = tts.setLanguage(new Locale("ar", "SA"));
        if (r < 0) r = tts.setLanguage(new Locale("ar"));
        if (r < 0) {
            lastText = "مفيش صوت عربي — نزّل اللغة العربية من إعدادات تحويل النص إلى كلام";
            updateNotification();
        }
        tts.setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build());
        tts.setSpeechRate(Prefs.ratePercent(this) / 100f);
        ttsReady = true;
        if (startPending) {
            startPending = false;
            handler.postDelayed(tick, 400);
        }
    }

    private int pick() {
        boolean[] on = Prefs.enabled(this);
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < on.length; i++) if (on[i]) ids.add(i);
        if (ids.isEmpty()) return -1;
        if (!Prefs.random(this)) {
            int pos = ids.indexOf(seq);
            seq = ids.get((pos + 1) % ids.size());
            return seq;
        }
        if (ids.size() == 1) return ids.get(0);
        int i;
        do { i = ids.get(rnd.nextInt(ids.size())); } while (i == last);
        return i;
    }

    private void sayNext() {
        int i = pick();
        if (i < 0) {
            lastText = "مفيش أذكار مختارة";
            updateNotification();
            return;
        }
        last = i;
        lastText = Adhkar.LIST[i][0];
        updateNotification();
        if (ttsReady) {
            tts.setSpeechRate(Prefs.ratePercent(this) / 100f);
            tts.speak(Adhkar.spoken(i), TextToSpeech.QUEUE_FLUSH, null, "dhikr" + System.currentTimeMillis());
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "تشغيل التذكير",
                NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("إشعار ثابت طول ما التذكير شغال");
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0,
            new Intent(this, MainActivity.class), piFlags);
        PendingIntent stop = PendingIntent.getService(this, 1,
            new Intent(this, DhikrService.class).setAction(ACTION_STOP), piFlags);
        PendingIntent now = PendingIntent.getService(this, 2,
            new Intent(this, DhikrService.class).setAction(ACTION_NOW), piFlags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL)
            : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("مذكّر الأذكار شغال")
            .setContentText(lastText)
            .setStyle(new Notification.BigTextStyle().bigText(lastText))
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(new Notification.Action.Builder(null, "ذكر الآن", now).build())
            .addAction(new Notification.Action.Builder(null, "إيقاف", stop).build());
        return b.build();
    }

    private void goForeground() {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification());
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

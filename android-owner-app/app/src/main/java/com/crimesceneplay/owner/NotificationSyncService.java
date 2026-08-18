package com.crimesceneplay.owner;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NotificationSyncService extends Service {
    private static final String CHANNEL_ALERTS = "reservation_alerts";
    private static final String CHANNEL_SYNC = "reservation_sync";
    private static final int FOREGROUND_ID = 7001;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    private SecurePrefs prefs;
    private NotificationStore store;

    static void start(Context context) {
        Intent intent = new Intent(context, NotificationSyncService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    static void requestSync(Context context) {
        Intent intent = new Intent(context, NotificationSyncService.class).setAction(AppConfig.ACTION_SYNC_NOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new SecurePrefs(this);
        store = new NotificationStore(this);
        createChannels();
        startForeground(FOREGROUND_ID, ongoingNotification("예약 알림을 확인하고 있습니다."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!prefs.isPaired()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && AppConfig.ACTION_SYNC_NOW.equals(intent.getAction())) executor.execute(this::syncOnce);
        if (running.compareAndSet(false, true)) executor.execute(this::runLoop);
        return START_STICKY;
    }

    private void runLoop() {
        while (running.get()) {
            syncOnce();
            try {
                Thread.sleep(prefs.getPollSeconds() * 1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void syncOnce() {
        if (!syncing.compareAndSet(false, true)) return;
        try {
            String token = prefs.getToken();
            if (token == null) return;
            long after = prefs.getLastId();
            ApiClient.FetchResult result = ApiClient.fetch(token, after, 100);
            prefs.setPollSeconds(result.pollSeconds);
            for (OwnerNotification item : result.notifications) {
                if (store.insert(item) && prefs.isInitialSyncDone()) postReservationNotification(item);
            }
            long newest = Math.max(result.newestId, store.maxId());
            if (newest > after) prefs.setLastId(newest);
            if (!prefs.isInitialSyncDone()) prefs.setInitialSyncDone(true);
            sendBroadcast(new Intent(AppConfig.ACTION_NEW_DATA).setPackage(getPackageName()));
        } catch (ApiClient.ApiException error) {
            if (error.status == 401) {
                prefs.clear();
                stopSelf();
                sendBroadcast(new Intent(AppConfig.ACTION_NEW_DATA).setPackage(getPackageName()));
            }
        } catch (Exception ignored) {
            // 네트워크가 돌아오면 다음 주기에 자동으로 다시 확인합니다.
        } finally {
            syncing.set(false);
        }
    }

    private void postReservationNotification(OwnerNotification item) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, (int) (item.id % Integer.MAX_VALUE), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String content = item.themeTitle + " · " + formatPlayDate(item.playDate) + " " + item.startTime
                + " · " + item.customerName + " " + item.partySize + "명";
        String big = content + "\n" + item.phone + " · " + item.bookingLabel
                + (item.specialRequest == null || item.specialRequest.trim().isEmpty() ? "" : "\n" + item.specialRequest);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ALERTS)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(notificationTitle(item))
                .setContentText(content)
                .setStyle(new Notification.BigTextStyle().bigText(big))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setColor(Color.rgb(183, 39, 45))
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis());
        getSystemService(NotificationManager.class).notify((int) (item.id % Integer.MAX_VALUE), builder.build());
    }

    private String notificationTitle(OwnerNotification item) {
        if ("CANCEL_REQUESTED".equals(item.eventType)) return "예약 취소 요청이 들어왔습니다";
        if ("CANCELED".equals(item.eventType)) return "예약이 취소되었습니다";
        return "새 예약이 들어왔습니다";
    }

    private String formatPlayDate(String date) {
        try {
            return LocalDate.parse(date).format(DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN));
        } catch (Exception error) {
            return date;
        }
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel alerts = new NotificationChannel(CHANNEL_ALERTS, "예약 알림", NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("새 예약과 취소 요청을 알려드립니다.");
        alerts.enableVibration(true);
        alerts.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationChannel sync = new NotificationChannel(CHANNEL_SYNC, "알림 수신 상태", NotificationManager.IMPORTANCE_MIN);
        sync.setDescription("예약 알림을 놓치지 않도록 서버 연결 상태를 유지합니다.");
        sync.setShowBadge(false);
        manager.createNotificationChannel(alerts);
        manager.createNotificationChannel(sync);
    }

    private Notification ongoingNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_SYNC)
                : new Notification.Builder(this);
        return builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("크라임씬플레이 예약 알림")
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setShowWhen(false)
                .build();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (prefs != null && prefs.isPaired()) {
            Intent restart = new Intent(getApplicationContext(), NotificationSyncService.class);
            PendingIntent pending = PendingIntent.getService(getApplicationContext(), 7701, restart,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
            alarm.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 5000L, pending);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        running.set(false);
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

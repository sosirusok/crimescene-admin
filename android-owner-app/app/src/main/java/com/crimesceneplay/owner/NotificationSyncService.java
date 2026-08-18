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

    private final ExecutorService realtimeExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService manualExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean manualSyncing = new AtomicBoolean(false);
    private final Object resultLock = new Object();
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
        startForeground(FOREGROUND_ID, ongoingNotification("실시간 예약 알림에 연결하고 있습니다."));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!prefs.isPaired()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && AppConfig.ACTION_SYNC_NOW.equals(intent.getAction())) {
            manualExecutor.execute(this::syncImmediately);
        }
        if (running.compareAndSet(false, true)) realtimeExecutor.execute(this::runRealtimeLoop);
        return START_STICKY;
    }

    private void runRealtimeLoop() {
        int retryDelay = AppConfig.NETWORK_RETRY_START_MS;
        while (running.get()) {
            try {
                String token = prefs.getToken();
                if (token == null) break;

                if (!prefs.isInitialSyncDone()) {
                    ApiClient.FetchResult initial = ApiClient.fetch(token, 0L, 300);
                    processResult(initial, false);
                    prefs.setInitialSyncDone(true);
                }

                updateForeground("실시간 알림 수신 중");
                long after = prefs.getLastId();
                ApiClient.FetchResult result = ApiClient.waitForNew(token, after, 100);
                processResult(result, true);
                retryDelay = AppConfig.NETWORK_RETRY_START_MS;
            } catch (ApiClient.ApiException error) {
                if (error.status == 401) {
                    prefs.clear();
                    sendBroadcast(new Intent(AppConfig.ACTION_NEW_DATA).setPackage(getPackageName()));
                    break;
                }
                updateForeground("서버 연결을 다시 시도하고 있습니다.");
                sleepRetry(retryDelay);
                retryDelay = Math.min(AppConfig.NETWORK_RETRY_MAX_MS, retryDelay * 2);
            } catch (Exception error) {
                updateForeground("인터넷 연결을 다시 시도하고 있습니다.");
                sleepRetry(retryDelay);
                retryDelay = Math.min(AppConfig.NETWORK_RETRY_MAX_MS, retryDelay * 2);
            }
        }
        running.set(false);
        stopSelf();
    }

    private void syncImmediately() {
        if (!manualSyncing.compareAndSet(false, true)) return;
        try {
            String token = prefs.getToken();
            if (token == null) return;
            ApiClient.FetchResult result = ApiClient.fetch(token, prefs.getLastId(), 100);
            processResult(result, prefs.isInitialSyncDone());
            if (!prefs.isInitialSyncDone()) prefs.setInitialSyncDone(true);
        } catch (ApiClient.ApiException error) {
            if (error.status == 401) {
                prefs.clear();
                stopSelf();
                sendBroadcast(new Intent(AppConfig.ACTION_NEW_DATA).setPackage(getPackageName()));
            }
        } catch (Exception ignored) {
            // 실시간 연결 루프가 네트워크 복구 뒤 자동으로 다시 확인합니다.
        } finally {
            manualSyncing.set(false);
        }
    }

    private void processResult(ApiClient.FetchResult result, boolean notifyUser) {
        synchronized (resultLock) {
            long before = prefs.getLastId();
            for (OwnerNotification item : result.notifications) {
                if (store.insert(item) && notifyUser && item.id > before) {
                    postReservationNotification(item);
                }
            }
            long newest = Math.max(result.newestId, store.maxId());
            if (newest > before) prefs.setLastId(newest);
        }
        sendBroadcast(new Intent(AppConfig.ACTION_NEW_DATA).setPackage(getPackageName()));
    }

    private void sleepRetry(int delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void updateForeground(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(FOREGROUND_ID, ongoingNotification(text));
    }

    private void postReservationNotification(OwnerNotification item) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;

        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                this,
                (int) (item.id % Integer.MAX_VALUE),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
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
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify((int) (item.id % Integer.MAX_VALUE), builder.build());
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
        if (manager == null) return;

        NotificationChannel alerts = new NotificationChannel(
                CHANNEL_ALERTS,
                "예약 알림",
                NotificationManager.IMPORTANCE_HIGH
        );
        alerts.setDescription("새 예약과 취소 요청을 바로 알려드립니다.");
        alerts.enableVibration(true);
        alerts.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        NotificationChannel sync = new NotificationChannel(
                CHANNEL_SYNC,
                "실시간 알림 연결",
                NotificationManager.IMPORTANCE_MIN
        );
        sync.setDescription("예약 알림을 놓치지 않도록 실시간 연결을 유지합니다.");
        sync.setShowBadge(false);
        manager.createNotificationChannel(alerts);
        manager.createNotificationChannel(sync);
    }

    private Notification ongoingNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
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
            PendingIntent pending = PendingIntent.getService(
                    getApplicationContext(),
                    7701,
                    restart,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
            );
            AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (alarm != null) {
                alarm.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 5_000L, pending);
            }
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        running.set(false);
        realtimeExecutor.shutdownNow();
        manualExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NotificationSyncService extends Service {
    private static final String CHANNEL_ALERTS = "reservation_alerts";
    private static final String CHANNEL_SYNC = "reservation_sync";
    private static final int FOREGROUND_ID = 7001;
    private static final int SUMMARY_ID = 7002;
    private static final int RESTART_REQUEST_CODE = 7701;

    private final ExecutorService realtimeExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService manualExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean manualSyncing = new AtomicBoolean(false);
    private final Object resultLock = new Object();

    private SecurePrefs prefs;
    private NotificationStore store;
    private volatile boolean stoppingIntentionally;

    static void start(Context context) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, NotificationSyncService.class);
        try {
            app.startForegroundService(intent);
        } catch (RuntimeException error) {
            scheduleRestart(app, 5_000L);
        }
    }

    static void requestSync(Context context) {
        Context app = context.getApplicationContext();
        Intent intent = new Intent(app, NotificationSyncService.class).setAction(AppConfig.ACTION_SYNC_NOW);
        try {
            app.startForegroundService(intent);
        } catch (RuntimeException error) {
            start(app);
        }
    }

    private static void scheduleRestart(Context context, long delayMs) {
        Context app = context.getApplicationContext();
        Intent restart = new Intent(app, NotificationSyncService.class);
        PendingIntent pending = PendingIntent.getForegroundService(
                app,
                RESTART_REQUEST_CODE,
                restart,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager alarm = (AlarmManager) app.getSystemService(ALARM_SERVICE);
        if (alarm != null) {
            alarm.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + Math.max(2_000L, delayMs),
                    pending
            );
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new SecurePrefs(this);
        store = new NotificationStore(this);
        createChannels();
        setState(AppConfig.STATE_CONNECTING, "서버에 연결하는 중", false);
        startForeground(FOREGROUND_ID, ongoingNotification("서버에 연결하는 중"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!prefs.isPaired()) {
            stoppingIntentionally = true;
            setState(AppConfig.STATE_STOPPED, "앱 연결이 필요합니다", false);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && AppConfig.ACTION_SYNC_NOW.equals(intent.getAction())) {
            manualExecutor.execute(this::syncImmediately);
        }
        if (running.compareAndSet(false, true)) {
            realtimeExecutor.execute(this::runRealtimeLoop);
        }
        return START_STICKY;
    }

    private void runRealtimeLoop() {
        int retryDelay = AppConfig.NETWORK_RETRY_START_MS;
        while (running.get()) {
            try {
                String token = prefs.getToken();
                if (token == null) break;

                if (!prefs.isInitialSyncDone()) {
                    setState(AppConfig.STATE_SYNCING, "예약 내역을 불러오는 중", false);
                    ApiClient.FetchResult initial = ApiClient.fetch(
                            token,
                            0L,
                            AppConfig.INITIAL_HISTORY_LIMIT
                    );
                    processResult(initial, false);
                    prefs.setInitialSyncDone(true);
                }

                setState(AppConfig.STATE_CONNECTED, "실시간으로 확인 중", true);
                updateForeground("실시간으로 예약을 확인하고 있습니다");

                long after = prefs.getLastId();
                ApiClient.FetchResult result = ApiClient.waitForNew(
                        token,
                        after,
                        AppConfig.INCREMENTAL_LIMIT
                );
                processResult(result, true);
                retryDelay = AppConfig.NETWORK_RETRY_START_MS;
            } catch (ApiClient.ApiException error) {
                if (error.status == 401) {
                    stoppingIntentionally = true;
                    prefs.clear();
                    broadcastDataChanged();
                    break;
                }
                setState(AppConfig.STATE_RETRYING, "서버 연결을 다시 시도하는 중", false);
                updateForeground("서버 연결을 다시 시도하고 있습니다");
                sleepRetry(retryDelay);
                retryDelay = Math.min(AppConfig.NETWORK_RETRY_MAX_MS, retryDelay * 2);
            } catch (Exception error) {
                setState(AppConfig.STATE_RETRYING, "인터넷 연결을 다시 시도하는 중", false);
                updateForeground("인터넷 연결을 다시 시도하고 있습니다");
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

            setState(AppConfig.STATE_SYNCING, "새 예약을 확인하는 중", false);
            ApiClient.FetchResult result = ApiClient.fetch(
                    token,
                    prefs.getLastId(),
                    AppConfig.INCREMENTAL_LIMIT
            );
            processResult(result, prefs.isInitialSyncDone());
            if (!prefs.isInitialSyncDone()) prefs.setInitialSyncDone(true);
            setState(AppConfig.STATE_CONNECTED, "실시간으로 확인 중", true);
            updateForeground("실시간으로 예약을 확인하고 있습니다");
        } catch (ApiClient.ApiException error) {
            if (error.status == 401) {
                stoppingIntentionally = true;
                prefs.clear();
                broadcastDataChanged();
                stopSelf();
            } else {
                setState(AppConfig.STATE_RETRYING, "서버 연결을 다시 시도하는 중", false);
            }
        } catch (Exception error) {
            setState(AppConfig.STATE_RETRYING, "인터넷 연결을 확인해 주세요", false);
        } finally {
            manualSyncing.set(false);
        }
    }

    private void processResult(ApiClient.FetchResult result, boolean notifyUser) {
        List<OwnerNotification> inserted = new ArrayList<>();
        synchronized (resultLock) {
            long before = prefs.getLastId();
            for (OwnerNotification item : result.notifications) {
                if (store.insert(item) && item.id > before) inserted.add(item);
            }

            long newest = Math.max(result.newestId, store.maxId());
            if (newest > before) prefs.setLastId(newest);
            prefs.setPollSeconds(result.pollSeconds);
            store.trimToMax(AppConfig.LOCAL_HISTORY_LIMIT);
        }

        if (notifyUser && !inserted.isEmpty()) {
            if (inserted.size() <= 5) {
                for (OwnerNotification item : inserted) postReservationNotification(item);
            } else {
                postSummaryNotification(inserted);
            }
        }
        broadcastDataChanged();
    }

    private void sleepRetry(int delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void setState(String state, String message, boolean successfulConnection) {
        prefs.setSyncState(state, message, successfulConnection);
        Intent intent = new Intent(AppConfig.ACTION_SYNC_STATE)
                .setPackage(getPackageName())
                .putExtra(AppConfig.EXTRA_SYNC_STATE, state)
                .putExtra(AppConfig.EXTRA_SYNC_MESSAGE, message);
        sendBroadcast(intent);
    }

    private void broadcastDataChanged() {
        sendBroadcast(new Intent(AppConfig.ACTION_NEW_DATA).setPackage(getPackageName()));
    }

    private void updateForeground(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(FOREGROUND_ID, ongoingNotification(text));
    }

    private void postReservationNotification(OwnerNotification item) {
        if (!canPostNotifications()) return;

        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                this,
                (int) (item.id % Integer.MAX_VALUE),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String content = formatPlayDate(item.playDate) + " " + item.startTime
                + ", " + item.themeTitle;
        StringBuilder big = new StringBuilder(content)
                .append("\n")
                .append(item.customerName)
                .append(", ")
                .append(item.partySize)
                .append("명, ")
                .append(item.bookingLabel)
                .append("\n")
                .append(item.phone);
        if (item.specialRequest != null && !item.specialRequest.trim().isEmpty()) {
            big.append("\n요청: ").append(item.specialRequest.trim());
        }

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(notificationTitle(item))
                .setContentText(content)
                .setStyle(new Notification.BigTextStyle().bigText(big.toString()))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setColor(Color.rgb(183, 39, 45))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis());

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify((int) (item.id % Integer.MAX_VALUE), builder.build());
    }

    private void postSummaryNotification(List<OwnerNotification> items) {
        if (!canPostNotifications()) return;

        OwnerNotification latest = items.get(items.size() - 1);
        Intent open = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                this,
                SUMMARY_ID,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String text = "새 알림 " + items.size() + "건이 도착했습니다";
        String big = text + "\n최근 알림: " + latest.themeTitle + ", "
                + formatPlayDate(latest.playDate) + " " + latest.startTime;
        Notification notification = new Notification.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("예약 알림이 여러 건 도착했습니다")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(big))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setColor(Color.rgb(183, 39, 45))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setShowWhen(true)
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(SUMMARY_ID, notification);
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
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
            return date == null ? "" : date;
        }
    }

    private void createChannels() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel alerts = new NotificationChannel(
                CHANNEL_ALERTS,
                "예약 알림",
                NotificationManager.IMPORTANCE_HIGH
        );
        alerts.setDescription("새 예약과 예약 취소를 알려드립니다.");
        alerts.enableVibration(true);
        alerts.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);

        NotificationChannel sync = new NotificationChannel(
                CHANNEL_SYNC,
                "예약 확인 연결",
                NotificationManager.IMPORTANCE_MIN
        );
        sync.setDescription("앱을 닫아도 새 예약을 확인합니다.");
        sync.setShowBadge(false);
        sync.setLockscreenVisibility(Notification.VISIBILITY_SECRET);

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
        return new Notification.Builder(this, CHANNEL_SYNC)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("예약 알림 수신 중")
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setShowWhen(false)
                .build();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (prefs != null && prefs.isPaired()) scheduleRestart(this, 5_000L);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        boolean shouldRestart = !stoppingIntentionally && prefs != null && prefs.isPaired();
        running.set(false);
        realtimeExecutor.shutdownNow();
        manualExecutor.shutdownNow();
        if (store != null) store.close();
        if (shouldRestart) scheduleRestart(this, 5_000L);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

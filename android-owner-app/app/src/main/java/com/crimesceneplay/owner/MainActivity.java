package com.crimesceneplay.owner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ColorStateList;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 9101;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger loadVersion = new AtomicInteger();

    private SecurePrefs prefs;
    private NotificationStore store;
    private ListView listView;
    private NotificationAdapter adapter;
    private TextView statusText;
    private TextView countText;
    private View statusDot;
    private ProgressBar progress;
    private LinearLayout permissionBanner;
    private Button refreshButton;
    private boolean receiverRegistered;
    private boolean showingPairScreen;

    private final BroadcastReceiver appReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!prefs.isPaired()) {
                if (!showingPairScreen) showPairScreen();
                return;
            }
            if (showingPairScreen) {
                showNotificationScreen();
                return;
            }
            updateStatus();
            if (AppConfig.ACTION_NEW_DATA.equals(intent.getAction())) reloadLocal(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(22, 24, 28));
        window.setNavigationBarColor(Color.rgb(22, 24, 28));
        prefs = new SecurePrefs(this);
        store = new NotificationStore(this);
        if (prefs.isPaired()) showNotificationScreen();
        else showPairScreen();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (prefs.isPaired() && !showingPairScreen) reloadLocal(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(AppConfig.ACTION_NEW_DATA);
        filter.addAction(AppConfig.ACTION_SYNC_STATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(appReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(appReceiver, filter);
        }
        receiverRegistered = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!showingPairScreen && prefs.isPaired()) {
            updatePermissionBanner();
            updateStatus();
        }
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(appReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void showPairScreen() {
        showingPairScreen = true;
        adapter = null;
        listView = null;
        statusText = null;
        countText = null;
        statusDot = null;
        progress = null;
        permissionBanner = null;
        refreshButton = null;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(245, 246, 248));

        LinearLayout root = vertical(Color.rgb(245, 246, 248));
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout body = vertical(Color.TRANSPARENT);
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView eyebrow = text("사장님 전용", 14, Color.rgb(174, 36, 43), Typeface.BOLD);
        body.addView(eyebrow);

        TextView title = text("예약 알림 연결", 30, Color.rgb(24, 26, 30), Typeface.BOLD);
        title.setPadding(0, dp(8), 0, dp(12));
        body.addView(title);

        TextView guide = text(
                "관리자 페이지에서 사용하는 암호를 입력하시면 됩니다. 한 번 연결하면 새 예약과 예약 취소가 이 휴대폰으로 옵니다.",
                16,
                Color.rgb(78, 82, 89),
                Typeface.NORMAL
        );
        guide.setLineSpacing(dp(2), 1.25f);
        body.addView(guide);

        LinearLayout card = vertical(Color.WHITE);
        card.setPadding(dp(20), dp(22), dp(20), dp(20));
        card.setBackground(rounded(Color.WHITE, 18, Color.rgb(222, 225, 230), 1));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(28);
        body.addView(card, cardParams);

        TextView label = text("관리자 암호", 14, Color.rgb(55, 59, 65), Typeface.BOLD);
        card.addView(label);

        EditText key = new EditText(this);
        key.setTextSize(17);
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setHint("암호 입력");
        key.setHintTextColor(Color.rgb(145, 149, 156));
        key.setTextColor(Color.rgb(24, 26, 30));
        key.setPadding(dp(14), 0, dp(14), 0);
        key.setBackground(rounded(Color.rgb(249, 249, 250), 12, Color.rgb(207, 211, 217), 1));
        LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56)
        );
        keyParams.topMargin = dp(9);
        card.addView(key, keyParams);

        Button connect = primaryButton("연결하기");
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56)
        );
        buttonParams.topMargin = dp(16);
        card.addView(connect, buttonParams);

        ProgressBar loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        loadingParams.gravity = Gravity.CENTER_HORIZONTAL;
        loadingParams.topMargin = dp(14);
        card.addView(loading, loadingParams);

        TextView help = text(
                "입력한 암호는 저장하지 않습니다.",
                13,
                Color.rgb(102, 106, 113),
                Typeface.NORMAL
        );
        help.setPadding(0, dp(17), 0, 0);
        card.addView(help);

        connect.setOnClickListener(view -> {
            String value = key.getText().toString().trim();
            if (value.length() < 8) {
                Toast.makeText(this, "관리자 암호를 확인해 주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            hideKeyboard(key);
            connect.setEnabled(false);
            connect.setText("연결 중");
            loading.setVisibility(View.VISIBLE);

            executor.execute(() -> {
                try {
                    ApiClient.PairResult pair = ApiClient.pair(value);
                    prefs.savePairing(pair.token, pair.pollSeconds);
                    try {
                        ApiClient.FetchResult initial = ApiClient.fetch(
                                pair.token,
                                0L,
                                AppConfig.INITIAL_HISTORY_LIMIT
                        );
                        store.insertAll(initial.notifications);
                        store.trimToMax(AppConfig.LOCAL_HISTORY_LIMIT);
                        prefs.setLastId(Math.max(initial.newestId, store.maxId()));
                        prefs.setInitialSyncDone(true);
                        prefs.setSyncState(AppConfig.STATE_CONNECTED, "새 예약을 확인하고 있습니다", true);
                    } catch (ApiClient.ApiException error) {
                        if (error.status == 401) {
                            prefs.clear();
                            throw error;
                        }
                    } catch (Exception ignored) {
                        prefs.setSyncState(AppConfig.STATE_RETRYING, "인터넷 연결을 확인하고 있습니다", false);
                    }

                    runOnUiThread(() -> {
                        showNotificationScreen();
                        requestNotificationPermission();
                        NotificationSyncService.start(this);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        connect.setEnabled(true);
                        connect.setText("연결하기");
                        loading.setVisibility(View.GONE);
                        Toast.makeText(this, friendlyError(error), Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        setContentView(scroll);
        key.requestFocus();
    }

    private void showNotificationScreen() {
        showingPairScreen = false;

        LinearLayout root = vertical(Color.rgb(245, 246, 248));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(18), dp(12), dp(16));
        header.setBackgroundColor(Color.rgb(22, 24, 28));

        LinearLayout heading = vertical(Color.TRANSPARENT);
        TextView name = text("예약 알림", 24, Color.WHITE, Typeface.BOLD);
        TextView branch = text(
                "크라임씬플레이 서면점",
                13,
                Color.rgb(190, 193, 199),
                Typeface.NORMAL
        );
        branch.setPadding(0, dp(2), 0, 0);
        heading.addView(name);
        heading.addView(branch);
        header.addView(heading, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        refreshButton = compactButton("새로고침");
        header.addView(refreshButton, new LinearLayout.LayoutParams(dp(88), dp(48)));

        Button settingsButton = compactButton("설정");
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(66), dp(48));
        settingsParams.leftMargin = dp(7);
        header.addView(settingsButton, settingsParams);
        root.addView(header);

        LinearLayout statusBar = new LinearLayout(this);
        statusBar.setOrientation(LinearLayout.HORIZONTAL);
        statusBar.setGravity(Gravity.CENTER_VERTICAL);
        statusBar.setPadding(dp(20), dp(13), dp(20), dp(13));
        statusBar.setBackgroundColor(Color.WHITE);
        statusBar.setElevation(dp(1));

        statusDot = new View(this);
        statusBar.addView(statusDot, new LinearLayout.LayoutParams(dp(10), dp(10)));

        statusText = text("예약 알림을 준비하는 중", 14, Color.rgb(56, 60, 67), Typeface.BOLD);
        statusText.setPadding(dp(9), 0, 0, 0);
        statusBar.addView(statusText, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        countText = text("", 13, Color.rgb(112, 116, 123), Typeface.NORMAL);
        statusBar.addView(countText);
        root.addView(statusBar);

        permissionBanner = new LinearLayout(this);
        permissionBanner.setOrientation(LinearLayout.HORIZONTAL);
        permissionBanner.setGravity(Gravity.CENTER_VERTICAL);
        permissionBanner.setPadding(dp(18), dp(11), dp(12), dp(11));
        permissionBanner.setBackgroundColor(Color.rgb(255, 246, 226));

        TextView permissionText = text(
                "휴대폰 알림이 꺼져 있습니다.",
                14,
                Color.rgb(108, 73, 13),
                Typeface.BOLD
        );
        permissionBanner.addView(permissionText, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        Button permissionButton = lightButton("알림 켜기");
        permissionBanner.addView(permissionButton, new LinearLayout.LayoutParams(dp(92), dp(44)));
        permissionButton.setOnClickListener(view -> openNotificationSettings());
        root.addView(permissionBanner);

        FrameLayout content = new FrameLayout(this);

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setPadding(dp(12), dp(10), dp(12), dp(18));
        listView.setClipToPadding(false);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setScrollingCacheEnabled(false);
        adapter = new NotificationAdapter();
        listView.setAdapter(adapter);
        content.addView(listView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout empty = vertical(Color.TRANSPARENT);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(32), dp(40), dp(32), dp(40));
        TextView emptyTitle = text(
                "아직 도착한 예약이 없습니다",
                20,
                Color.rgb(45, 49, 55),
                Typeface.BOLD
        );
        emptyTitle.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle);
        TextView emptyGuide = text(
                "새 예약이 들어오면 이 화면에 바로 표시됩니다.",
                15,
                Color.rgb(105, 109, 116),
                Typeface.NORMAL
        );
        emptyGuide.setGravity(Gravity.CENTER);
        emptyGuide.setPadding(0, dp(9), 0, 0);
        empty.addView(emptyGuide);
        empty.setVisibility(View.GONE);
        content.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        listView.setEmptyView(empty);

        progress = new ProgressBar(this);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                dp(42),
                dp(42),
                Gravity.CENTER
        );
        content.addView(progress, progressParams);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        listView.setOnItemClickListener((parent, view, position, id) -> {
            OwnerNotification item = adapter.getItem(position);
            if (item != null) showDetail(item);
        });

        refreshButton.setOnClickListener(view -> {
            refreshButton.setEnabled(false);
            refreshButton.setText("확인 중");
            prefs.setSyncState(AppConfig.STATE_SYNCING, "새 예약을 확인하는 중", false);
            updateStatus();
            NotificationSyncService.requestSync(this);
            refreshButton.postDelayed(() -> {
                if (refreshButton != null) {
                    refreshButton.setEnabled(true);
                    refreshButton.setText("새로고침");
                }
            }, 3_000L);
        });

        settingsButton.setOnClickListener(view -> showSettingsMenu());

        setContentView(root);
        updatePermissionBanner();
        updateStatus();
        reloadLocal(true);
        NotificationSyncService.start(this);
    }

    private void reloadLocal(boolean showLoading) {
        if (adapter == null) return;
        int version = loadVersion.incrementAndGet();
        if (showLoading && progress != null) progress.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            List<OwnerNotification> items = store.listRecent(AppConfig.LOCAL_HISTORY_LIMIT);
            runOnUiThread(() -> {
                if (version != loadVersion.get() || adapter == null) return;
                adapter.setItems(items);
                if (countText != null) countText.setText(items.isEmpty() ? "" : items.size() + "건");
                if (progress != null) progress.setVisibility(View.GONE);
            });
        });
    }

    private void updateStatus() {
        if (statusText == null || statusDot == null) return;

        if (!notificationsAllowed()) {
            setStatus(Color.rgb(190, 73, 38), "휴대폰 알림이 꺼져 있습니다");
            return;
        }

        String state = prefs.getSyncState();
        String message = prefs.getSyncMessage();
        if (AppConfig.STATE_CONNECTED.equals(state)) {
            setStatus(Color.rgb(38, 145, 86), message.isEmpty() ? "새 예약을 확인하고 있습니다" : message);
        } else if (AppConfig.STATE_RETRYING.equals(state)) {
            setStatus(Color.rgb(202, 119, 30), message.isEmpty() ? "연결을 다시 시도하고 있습니다" : message);
        } else if (AppConfig.STATE_STOPPED.equals(state)) {
            setStatus(Color.rgb(184, 48, 55), message.isEmpty() ? "다시 연결해 주세요" : message);
        } else {
            setStatus(Color.rgb(87, 101, 122), message.isEmpty() ? "예약 알림을 준비하는 중" : message);
        }
    }

    private void setStatus(int color, String message) {
        statusDot.setBackground(rounded(color, 6, Color.TRANSPARENT, 0));
        statusText.setText(message);
    }

    private void updatePermissionBanner() {
        if (permissionBanner == null) return;
        permissionBanner.setVisibility(notificationsAllowed() ? View.GONE : View.VISIBLE);
    }

    private boolean notificationsAllowed() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) return false;
        NotificationChannel channel = manager.getNotificationChannel(NotificationSyncService.CHANNEL_ALERTS);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            updatePermissionBanner();
            updateStatus();
            if (!notificationsAllowed()) {
                Toast.makeText(this, "알림이 꺼져 있으면 새 예약을 소리로 알려드릴 수 없습니다.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showSettingsMenu() {
        String[] items = {"알림 설정", "앱 연결 해제"};
        new AlertDialog.Builder(this)
                .setTitle("설정")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) openNotificationSettings();
                    else confirmDisconnect();
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    private void showDetail(OwnerNotification item) {
        StringBuilder message = new StringBuilder();
        message.append(item.themeTitle).append("\n")
                .append(formatPlayDate(item.playDate)).append(" ").append(item.startTime).append("\n\n")
                .append("예약자  ").append(item.customerName).append("\n")
                .append("연락처  ").append(item.phone).append("\n")
                .append("인원  ").append(item.partySize).append("명\n")
                .append("예약 방식  ").append(item.bookingLabel).append("\n")
                .append("접수 경로  ").append(item.sourceLabel).append("\n")
                .append("이용 금액  ").append(formatMoney(item.totalAmount));
        if (item.specialRequest != null && !item.specialRequest.trim().isEmpty()) {
            message.append("\n\n요청 사항\n").append(item.specialRequest.trim());
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(item.title)
                .setMessage(message.toString())
                .setNegativeButton("닫기", null);

        String number = callableNumber(item.phone);
        if (!number.isEmpty()) {
            builder.setPositiveButton("전화하기", (dialog, which) ->
                    startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number))));
        }
        builder.show();
    }

    private void confirmDisconnect() {
        new AlertDialog.Builder(this)
                .setTitle("이 휴대폰의 연결을 해제하시겠습니까?")
                .setMessage("이후 예약 알림이 오지 않으며, 이 휴대폰에 저장된 알림 내역도 지워집니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("연결 해제", (dialog, which) -> {
                    String token = prefs.getToken();
                    executor.execute(() -> {
                        try {
                            if (token != null) ApiClient.disconnect(token);
                        } catch (Exception ignored) {
                            // 연결 정보는 다음 정리 때 자동으로 삭제됩니다.
                        }
                        prefs.clear();
                        store.clearAll();
                        stopService(new Intent(this, NotificationSyncService.class));
                        runOnUiThread(this::showPairScreen);
                    });
                })
                .show();
    }

    private String friendlyError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ApiClient.ApiException) {
                String message = current.getMessage();
                return message == null || message.trim().isEmpty()
                        ? "연결하지 못했습니다. 잠시 후 다시 시도해 주세요."
                        : message;
            }
            if (current instanceof UnknownHostException) return "인터넷 연결을 확인해 주세요.";
            if (current instanceof SocketTimeoutException) return "연결이 지연되고 있습니다. 잠시 후 다시 시도해 주세요.";
            current = current.getCause();
        }
        return "연결하지 못했습니다. 잠시 후 다시 시도해 주세요.";
    }

    private String callableNumber(String phone) {
        if (phone == null) return "";
        String value = phone.replaceAll("[^0-9+]", "");
        return value.replace("+", "").length() >= 8 ? value : "";
    }

    private void hideKeyboard(View view) {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private final class NotificationAdapter extends BaseAdapter {
        private final ArrayList<OwnerNotification> items = new ArrayList<>();

        void setItems(List<OwnerNotification> values) {
            items.clear();
            if (values != null) items.addAll(values);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public OwnerNotification getItem(int position) {
            return position >= 0 && position < items.size() ? items.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            OwnerNotification item = getItem(position);
            return item == null ? 0L : item.id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CardHolder holder;
            FrameLayout wrapper;
            if (convertView instanceof FrameLayout && convertView.getTag() instanceof CardHolder) {
                wrapper = (FrameLayout) convertView;
                holder = (CardHolder) convertView.getTag();
            } else {
                wrapper = new FrameLayout(MainActivity.this);
                wrapper.setPadding(0, 0, 0, dp(9));
                wrapper.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                LinearLayout card = vertical(Color.WHITE);
                card.setPadding(dp(16), dp(15), dp(16), dp(15));
                card.setElevation(dp(1));
                wrapper.addView(card, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                LinearLayout first = new LinearLayout(MainActivity.this);
                first.setOrientation(LinearLayout.HORIZONTAL);
                first.setGravity(Gravity.CENTER_VERTICAL);
                TextView event = text("", 13, Color.rgb(38, 133, 78), Typeface.BOLD);
                first.addView(event, new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                ));
                TextView time = text("", 12, Color.rgb(122, 126, 132), Typeface.NORMAL);
                first.addView(time);
                card.addView(first);

                TextView theme = text("", 18, Color.rgb(23, 25, 29), Typeface.BOLD);
                theme.setPadding(0, dp(8), 0, dp(5));
                card.addView(theme);

                TextView schedule = text("", 15, Color.rgb(61, 65, 72), Typeface.NORMAL);
                card.addView(schedule);

                TextView customer = text("", 14, Color.rgb(85, 89, 96), Typeface.NORMAL);
                customer.setPadding(0, dp(6), 0, 0);
                card.addView(customer);

                TextView request = text("", 14, Color.rgb(73, 77, 84), Typeface.NORMAL);
                request.setPadding(dp(12), dp(9), dp(12), dp(9));
                request.setBackground(rounded(Color.rgb(247, 247, 248), 10, Color.TRANSPARENT, 0));
                LinearLayout.LayoutParams requestParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                requestParams.topMargin = dp(10);
                card.addView(request, requestParams);

                holder = new CardHolder(card, event, time, theme, schedule, customer, request);
                wrapper.setTag(holder);
            }

            OwnerNotification item = getItem(position);
            if (item == null) return wrapper;

            boolean newReservation = "NEW_RESERVATION".equals(item.eventType);
            int border = newReservation ? Color.rgb(218, 221, 226) : Color.rgb(235, 193, 196);
            holder.card.setBackground(rounded(Color.WHITE, 14, border, 1));
            holder.event.setText(item.title);
            holder.event.setTextColor(newReservation ? Color.rgb(37, 132, 78) : Color.rgb(174, 36, 43));
            holder.time.setText(formatCreatedAt(item.createdAt));
            holder.theme.setText(item.themeTitle);
            holder.schedule.setText(
                    formatPlayDate(item.playDate) + " " + item.startTime
                            + "  /  " + item.partySize + "명"
                            + "  /  " + item.bookingLabel
            );
            holder.customer.setText(item.customerName + "  " + item.phone);

            String request = item.specialRequest == null ? "" : item.specialRequest.trim();
            if (request.isEmpty()) {
                holder.request.setVisibility(View.GONE);
            } else {
                holder.request.setText("요청  " + request);
                holder.request.setVisibility(View.VISIBLE);
            }
            return wrapper;
        }
    }

    private static final class CardHolder {
        final LinearLayout card;
        final TextView event;
        final TextView time;
        final TextView theme;
        final TextView schedule;
        final TextView customer;
        final TextView request;

        CardHolder(
                LinearLayout card,
                TextView event,
                TextView time,
                TextView theme,
                TextView schedule,
                TextView customer,
                TextView request
        ) {
            this.card = card;
            this.event = event;
            this.time = time;
            this.theme = theme;
            this.schedule = schedule;
            this.customer = customer;
            this.request = request;
        }
    }

    private LinearLayout vertical(int color) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(color);
        return layout;
    }

    private TextView text(String value, float size, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", style));
        view.setIncludeFontPadding(false);
        return view;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(17);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(rippleRounded(
                Color.rgb(174, 36, 43),
                12,
                Color.argb(55, 255, 255, 255),
                Color.TRANSPARENT,
                0
        ));
        return button;
    }

    private Button compactButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(rippleRounded(
                Color.rgb(51, 54, 60),
                10,
                Color.argb(50, 255, 255, 255),
                Color.rgb(79, 83, 90),
                1
        ));
        return button;
    }

    private Button lightButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(Color.rgb(98, 65, 10));
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setAllCaps(false);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(rippleRounded(
                Color.rgb(255, 252, 242),
                10,
                Color.argb(35, 126, 88, 22),
                Color.rgb(226, 195, 134),
                1
        ));
        return button;
    }

    private RippleDrawable rippleRounded(int fill, int radiusDp, int ripple, int stroke, int strokeWidthDp) {
        GradientDrawable content = rounded(fill, radiusDp, stroke, strokeWidthDp);
        return new RippleDrawable(ColorStateList.valueOf(ripple), content, null);
    }

    private GradientDrawable rounded(int fill, int radiusDp, int stroke, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) drawable.setStroke(dp(strokeWidthDp), stroke);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String formatPlayDate(String value) {
        try {
            LocalDate date = LocalDate.parse(value);
            return date.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN));
        } catch (Exception error) {
            return value == null ? "" : value;
        }
    }

    private String formatCreatedAt(String value) {
        try {
            return OffsetDateTime.parse(value)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREAN));
        } catch (Exception error) {
            return "";
        }
    }

    private String formatMoney(int value) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(value) + "원";
    }
}

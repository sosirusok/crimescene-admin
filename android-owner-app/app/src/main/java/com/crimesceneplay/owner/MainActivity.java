package com.crimesceneplay.owner;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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

public final class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SecurePrefs prefs;
    private NotificationStore store;
    private ListView listView;
    private NotificationAdapter adapter;
    private TextView statusText;
    private ProgressBar progress;
    private boolean receiverRegistered;

    private final BroadcastReceiver dataReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!prefs.isPaired()) showPairScreen();
            else reloadLocal();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(21, 23, 27));
        window.setNavigationBarColor(Color.rgb(21, 23, 27));
        prefs = new SecurePrefs(this);
        store = new NotificationStore(this);
        if (prefs.isPaired()) showNotificationScreen();
        else showPairScreen();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(AppConfig.ACTION_NEW_DATA);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(dataReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(dataReceiver, filter);
        receiverRegistered = true;
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(dataReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        store.close();
        super.onDestroy();
    }

    private void showPairScreen() {
        LinearLayout root = vertical(Color.rgb(244, 245, 247));
        root.setPadding(dp(24), dp(54), dp(24), dp(24));

        TextView eyebrow = text("사장님 전용", 14, Color.rgb(183, 39, 45), Typeface.BOLD);
        root.addView(eyebrow);
        TextView title = text("예약 알림 앱 연결", 30, Color.rgb(23, 25, 29), Typeface.BOLD);
        title.setPadding(0, dp(8), 0, dp(12));
        root.addView(title);
        TextView guide = text("관리자 페이지에서 사용하시는 암호키를 한 번만 입력해 주세요. 연결 후에는 새 예약이 들어올 때 이 휴대폰으로 알림이 옵니다.", 16, Color.rgb(82, 86, 93), Typeface.NORMAL);
        guide.setLineSpacing(0, 1.35f);
        root.addView(guide);

        LinearLayout card = vertical(Color.WHITE);
        card.setPadding(dp(20), dp(22), dp(20), dp(20));
        card.setBackground(rounded(Color.WHITE, 18, Color.rgb(226, 228, 232), 1));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(28);
        root.addView(card, cardParams);

        TextView label = text("관리자 암호키", 14, Color.rgb(65, 68, 74), Typeface.BOLD);
        card.addView(label);
        EditText key = new EditText(this);
        key.setTextSize(17);
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setHint("암호키 입력");
        key.setPadding(dp(14), 0, dp(14), 0);
        key.setBackground(rounded(Color.rgb(248, 248, 249), 12, Color.rgb(214, 216, 220), 1));
        LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        keyParams.topMargin = dp(9);
        card.addView(key, keyParams);

        Button connect = new Button(this);
        connect.setText("앱 연결하기");
        connect.setTextSize(17);
        connect.setTextColor(Color.WHITE);
        connect.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        connect.setAllCaps(false);
        connect.setBackground(rounded(Color.rgb(183, 39, 45), 12, Color.TRANSPARENT, 0));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        buttonParams.topMargin = dp(16);
        card.addView(connect, buttonParams);

        ProgressBar loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        loadingParams.gravity = Gravity.CENTER_HORIZONTAL;
        loadingParams.topMargin = dp(14);
        card.addView(loading, loadingParams);

        TextView help = text("암호키는 휴대폰 안에 암호화해서 보관하며 서버에는 원문을 저장하지 않습니다.", 13, Color.rgb(103, 107, 114), Typeface.NORMAL);
        help.setPadding(0, dp(18), 0, 0);
        help.setLineSpacing(0, 1.25f);
        card.addView(help);

        connect.setOnClickListener(view -> {
            String value = key.getText().toString();
            if (value.length() < 8) {
                Toast.makeText(this, "관리자 암호키를 확인해 주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            connect.setEnabled(false);
            loading.setVisibility(View.VISIBLE);
            executor.execute(() -> {
                try {
                    ApiClient.PairResult pair = ApiClient.pair(value);
                    prefs.savePairing(pair.token, pair.pollSeconds);
                    ApiClient.FetchResult initial = ApiClient.fetch(pair.token, 0L, 300);
                    store.insertAll(initial.notifications);
                    prefs.setLastId(Math.max(initial.newestId, store.maxId()));
                    prefs.setInitialSyncDone(true);
                    runOnUiThread(() -> {
                        requestNotificationPermission();
                        showNotificationScreen();
                        NotificationSyncService.start(this);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        connect.setEnabled(true);
                        loading.setVisibility(View.GONE);
                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        setContentView(root);
    }

    private void showNotificationScreen() {
        requestNotificationPermission();
        LinearLayout root = vertical(Color.rgb(244, 245, 247));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(20), dp(12), dp(16));
        header.setBackgroundColor(Color.rgb(21, 23, 27));

        LinearLayout heading = vertical(Color.TRANSPARENT);
        TextView name = text("예약 알림", 24, Color.WHITE, Typeface.BOLD);
        TextView branch = text("크라임씬플레이 서면1호점", 13, Color.rgb(188, 191, 197), Typeface.NORMAL);
        heading.addView(name);
        heading.addView(branch);
        header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button refresh = compactButton("새로고침");
        header.addView(refresh, new LinearLayout.LayoutParams(dp(92), dp(42)));
        root.addView(header);

        LinearLayout statusBar = new LinearLayout(this);
        statusBar.setOrientation(LinearLayout.HORIZONTAL);
        statusBar.setGravity(Gravity.CENTER_VERTICAL);
        statusBar.setPadding(dp(20), dp(12), dp(20), dp(12));
        statusBar.setBackgroundColor(Color.WHITE);
        View dot = new View(this);
        dot.setBackground(rounded(Color.rgb(41, 157, 92), 6, Color.TRANSPARENT, 0));
        statusBar.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
        statusText = text("알림 수신 중", 14, Color.rgb(63, 67, 73), Typeface.BOLD);
        statusText.setPadding(dp(9), 0, 0, 0);
        statusBar.addView(statusText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView disconnect = text("연결 해제", 13, Color.rgb(120, 123, 129), Typeface.NORMAL);
        disconnect.setPadding(dp(12), dp(8), dp(4), dp(8));
        statusBar.addView(disconnect);
        root.addView(statusBar);

        FrameLayout content = new FrameLayout(this);
        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setPadding(dp(12), dp(10), dp(12), dp(22));
        listView.setClipToPadding(false);
        adapter = new NotificationAdapter();
        listView.setAdapter(adapter);
        content.addView(listView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER);
        content.addView(progress, progressParams);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        listView.setOnItemClickListener((parent, view, position, id) -> showDetail(adapter.getItem(position)));
        refresh.setOnClickListener(view -> {
            statusText.setText("새 예약을 확인하고 있습니다");
            progress.setVisibility(View.VISIBLE);
            NotificationSyncService.requestSync(this);
            listView.postDelayed(() -> {
                progress.setVisibility(View.GONE);
                statusText.setText("알림 수신 중");
                reloadLocal();
            }, 1800L);
        });
        disconnect.setOnClickListener(view -> confirmDisconnect());

        setContentView(root);
        reloadLocal();
        NotificationSyncService.start(this);
    }

    private void reloadLocal() {
        if (adapter == null) return;
        List<OwnerNotification> items = store.listAll();
        adapter.setItems(items);
        statusText.setText(items.isEmpty() ? "새 예약을 기다리고 있습니다" : "알림 수신 중 · " + items.size() + "건 보관");
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
        if (item.specialRequest != null && !item.specialRequest.trim().isEmpty()) message.append("\n\n요청 사항\n").append(item.specialRequest);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(item.title)
                .setMessage(message.toString())
                .setNegativeButton("닫기", null)
                .setPositiveButton("전화하기", (d, which) -> {
                    String number = item.phone == null ? "" : item.phone.replaceAll("[^0-9+]", "");
                    if (!number.isEmpty()) startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
                })
                .create();
        dialog.show();
    }

    private void confirmDisconnect() {
        new AlertDialog.Builder(this)
                .setTitle("앱 연결을 해제하시겠습니까?")
                .setMessage("이 휴대폰으로 예약 알림이 더 이상 오지 않습니다. 저장된 알림 내역도 함께 지워집니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("연결 해제", (dialog, which) -> {
                    String token = prefs.getToken();
                    executor.execute(() -> {
                        try {
                            if (token != null) ApiClient.disconnect(token);
                        } catch (Exception ignored) {}
                        prefs.clear();
                        store.clearAll();
                        stopService(new Intent(this, NotificationSyncService.class));
                        runOnUiThread(this::showPairScreen);
                    });
                }).show();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9101);
        }
    }

    private final class NotificationAdapter extends BaseAdapter {
        private final ArrayList<OwnerNotification> items = new ArrayList<>();

        void setItems(List<OwnerNotification> values) {
            items.clear();
            items.addAll(values);
            notifyDataSetChanged();
        }

        @Override public int getCount() { return items.size(); }
        @Override public OwnerNotification getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return items.get(position).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            OwnerNotification item = getItem(position);
            LinearLayout card = vertical(Color.WHITE);
            card.setPadding(dp(16), dp(15), dp(16), dp(15));
            int border = "NEW_RESERVATION".equals(item.eventType) ? Color.rgb(218, 220, 224) : Color.rgb(238, 199, 201);
            card.setBackground(rounded(Color.WHITE, 14, border, 1));
            LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            outer.setMargins(0, 0, 0, dp(9));
            card.setLayoutParams(outer);

            LinearLayout first = new LinearLayout(MainActivity.this);
            first.setOrientation(LinearLayout.HORIZONTAL);
            first.setGravity(Gravity.CENTER_VERTICAL);
            TextView event = text(item.title, 13,
                    "NEW_RESERVATION".equals(item.eventType) ? Color.rgb(41, 126, 77) : Color.rgb(183, 39, 45), Typeface.BOLD);
            first.addView(event, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView time = text(formatCreatedAt(item.createdAt), 12, Color.rgb(122, 126, 132), Typeface.NORMAL);
            first.addView(time);
            card.addView(first);

            TextView theme = text(item.themeTitle, 18, Color.rgb(23, 25, 29), Typeface.BOLD);
            theme.setPadding(0, dp(8), 0, dp(5));
            card.addView(theme);
            TextView schedule = text(formatPlayDate(item.playDate) + "  " + item.startTime + "  ·  " + item.partySize + "명  ·  " + item.bookingLabel,
                    15, Color.rgb(64, 68, 74), Typeface.NORMAL);
            card.addView(schedule);
            TextView customer = text(item.customerName + "  ·  " + item.phone, 14, Color.rgb(88, 92, 98), Typeface.NORMAL);
            customer.setPadding(0, dp(6), 0, 0);
            card.addView(customer);
            if (item.specialRequest != null && !item.specialRequest.trim().isEmpty()) {
                TextView request = text(item.specialRequest, 14, Color.rgb(82, 86, 93), Typeface.NORMAL);
                request.setPadding(dp(12), dp(9), dp(12), dp(9));
                request.setBackground(rounded(Color.rgb(247, 247, 248), 10, Color.TRANSPARENT, 0));
                LinearLayout.LayoutParams requestParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                requestParams.topMargin = dp(10);
                card.addView(request, requestParams);
            }
            return card;
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
        view.setTypeface(Typeface.DEFAULT, style);
        return view;
    }

    private Button compactButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(rounded(Color.rgb(53, 56, 62), 10, Color.rgb(82, 85, 91), 1));
        return button;
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
            return value;
        }
    }

    private String formatCreatedAt(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("M/d HH:mm", Locale.KOREAN));
        } catch (Exception error) {
            return "";
        }
    }

    private String formatMoney(int value) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(value) + "원";
    }
}

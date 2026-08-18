package com.crimesceneplay.owner;

import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ApiClient {
    static final class PairResult {
        final String token;
        final int pollSeconds;

        PairResult(String token, int pollSeconds) {
            this.token = token;
            this.pollSeconds = pollSeconds;
        }
    }

    static final class FetchResult {
        final List<OwnerNotification> notifications;
        final long newestId;
        final int pollSeconds;

        FetchResult(List<OwnerNotification> notifications, long newestId, int pollSeconds) {
            this.notifications = notifications;
            this.newestId = newestId;
            this.pollSeconds = pollSeconds;
        }
    }

    private ApiClient() {}

    static PairResult pair(String accessKey) throws Exception {
        JSONObject body = new JSONObject();
        body.put("accessKey", accessKey);
        body.put("deviceName", deviceName());
        body.put("appVersion", AppConfig.APP_VERSION);
        JSONObject json = request("POST", "/pair", null, body);
        String token = json.optString("token", "");
        if (token.length() < 32) throw new IOException("앱 연결 정보를 받지 못했습니다.");
        return new PairResult(token, json.optInt("pollSeconds", AppConfig.DEFAULT_POLL_SECONDS));
    }

    static FetchResult fetch(String token, long after, int limit) throws Exception {
        JSONObject json = request("GET", "/notifications?after=" + Math.max(0L, after) + "&limit=" + limit, token, null);
        JSONArray array = json.optJSONArray("notifications");
        ArrayList<OwnerNotification> items = new ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) items.add(OwnerNotification.fromJson(array.getJSONObject(i)));
        }
        return new FetchResult(items, json.optLong("newestId", after),
                json.optInt("pollSeconds", AppConfig.DEFAULT_POLL_SECONDS));
    }

    static void disconnect(String token) throws Exception {
        request("POST", "/disconnect", token, new JSONObject());
    }

    private static JSONObject request(String method, String path, String token, JSONObject body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(AppConfig.API_BASE + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(15_000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("apikey", AppConfig.PUBLISHABLE_KEY);
        connection.setRequestProperty("X-Client-Info", "crimescene-owner-android/" + AppConfig.APP_VERSION);
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String text = read(stream);
        connection.disconnect();
        JSONObject json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
        if (status >= 400) throw new ApiException(status, json.optString("error", "요청을 처리하지 못했습니다."));
        return json;
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private static String deviceName() {
        String manufacturer = Build.MANUFACTURER == null ? "Android" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? "휴대폰" : Build.MODEL.trim();
        if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) return model;
        return manufacturer + " " + model;
    }

    static final class ApiException extends IOException {
        final int status;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}

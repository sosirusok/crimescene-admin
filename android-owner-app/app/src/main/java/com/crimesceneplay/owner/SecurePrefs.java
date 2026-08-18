package com.crimesceneplay.owner;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecurePrefs {
    private static final String PREFS = "owner_secure_state";
    private static final String KEY_ALIAS = "crimescene_owner_local_v1";
    private static final String TOKEN = "paired_token";
    private static final String LAST_ID = "last_notification_id";
    private static final String INITIAL_SYNC = "initial_sync_done";
    private static final String POLL_SECONDS = "poll_seconds";
    private static final String SYNC_STATE = "sync_state";
    private static final String SYNC_MESSAGE = "sync_message";
    private static final String LAST_SYNC_AT = "last_sync_at";

    private static volatile SecretKey cachedKey;

    private final SharedPreferences prefs;

    SecurePrefs(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        migrateVisibleStatusText();
    }

    private void migrateVisibleStatusText() {
        String message = prefs.getString(SYNC_MESSAGE, "");
        if (message == null || message.isEmpty() || message.contains("서버")) {
            prefs.edit()
                    .putString(SYNC_STATE, AppConfig.STATE_CONNECTING)
                    .putString(SYNC_MESSAGE, "예약 알림을 준비하는 중")
                    .apply();
        } else if ("실시간으로 확인 중".equals(message) || "실시간 알림 수신 중".equals(message)) {
            prefs.edit().putString(SYNC_MESSAGE, "새 예약을 확인하고 있습니다").apply();
        }
    }

    boolean isPaired() {
        return getToken() != null;
    }

    String getToken() {
        String encrypted = prefs.getString(TOKEN, null);
        if (encrypted == null) return null;
        try {
            return decrypt(encrypted);
        } catch (Exception error) {
            clear();
            return null;
        }
    }

    void savePairing(String token, int pollSeconds) throws Exception {
        prefs.edit()
                .putString(TOKEN, encrypt(token))
                .putLong(LAST_ID, 0L)
                .putBoolean(INITIAL_SYNC, false)
                .putInt(POLL_SECONDS, Math.max(15, pollSeconds))
                .putString(SYNC_STATE, AppConfig.STATE_CONNECTING)
                .putString(SYNC_MESSAGE, "예약 알림을 준비하는 중")
                .putLong(LAST_SYNC_AT, 0L)
                .apply();
    }

    long getLastId() {
        return prefs.getLong(LAST_ID, 0L);
    }

    void setLastId(long id) {
        prefs.edit().putLong(LAST_ID, Math.max(0L, id)).apply();
    }

    boolean isInitialSyncDone() {
        return prefs.getBoolean(INITIAL_SYNC, false);
    }

    void setInitialSyncDone(boolean value) {
        prefs.edit().putBoolean(INITIAL_SYNC, value).apply();
    }

    int getPollSeconds() {
        return Math.max(15, prefs.getInt(POLL_SECONDS, AppConfig.DEFAULT_POLL_SECONDS));
    }

    void setPollSeconds(int seconds) {
        prefs.edit().putInt(POLL_SECONDS, Math.max(15, seconds)).apply();
    }

    void setSyncState(String state, String message, boolean successfulConnection) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString(SYNC_STATE, state)
                .putString(SYNC_MESSAGE, message == null ? "" : message);
        if (successfulConnection) editor.putLong(LAST_SYNC_AT, System.currentTimeMillis());
        editor.apply();
    }

    String getSyncState() {
        return prefs.getString(SYNC_STATE, AppConfig.STATE_CONNECTING);
    }

    String getSyncMessage() {
        return prefs.getString(SYNC_MESSAGE, "예약 알림을 준비하는 중");
    }

    long getLastSyncAt() {
        return prefs.getLong(LAST_SYNC_AT, 0L);
    }

    void clear() {
        prefs.edit().clear().apply();
    }

    static String encryptLocal(String value) throws Exception {
        return encrypt(value);
    }

    static String decryptLocal(String value) throws Exception {
        return decrypt(value);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        SecretKey key = cachedKey;
        if (key != null) return key;

        synchronized (SecurePrefs.class) {
            key = cachedKey;
            if (key != null) return key;

            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) {
                key = ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
            } else {
                KeyGenerator generator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        "AndroidKeyStore"
                );
                generator.init(new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build());
                key = generator.generateKey();
            }
            cachedKey = key;
            return key;
        }
    }

    private static String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] packed = new byte[1 + iv.length + encrypted.length];
        packed[0] = (byte) iv.length;
        System.arraycopy(iv, 0, packed, 1, iv.length);
        System.arraycopy(encrypted, 0, packed, 1 + iv.length, encrypted.length);
        return Base64.encodeToString(packed, Base64.NO_WRAP);
    }

    private static String decrypt(String value) throws Exception {
        byte[] packed = Base64.decode(value, Base64.NO_WRAP);
        if (packed.length < 14) throw new IllegalArgumentException("Invalid encrypted value");
        int ivLength = packed[0] & 0xff;
        if (ivLength < 12 || packed.length <= 1 + ivLength) {
            throw new IllegalArgumentException("Invalid encrypted value");
        }
        byte[] iv = new byte[ivLength];
        byte[] encrypted = new byte[packed.length - 1 - ivLength];
        System.arraycopy(packed, 1, iv, 0, ivLength);
        System.arraycopy(packed, 1 + ivLength, encrypted, 0, encrypted.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }
}

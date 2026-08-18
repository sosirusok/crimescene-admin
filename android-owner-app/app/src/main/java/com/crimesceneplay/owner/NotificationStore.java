package com.crimesceneplay.owner;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class NotificationStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "owner_notifications.db";
    private static final int DB_VERSION = 1;

    NotificationStore(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notifications (" +
                "id INTEGER PRIMARY KEY," +
                "event_type TEXT NOT NULL," +
                "title TEXT NOT NULL," +
                "reservation_id TEXT NOT NULL," +
                "theme_title TEXT NOT NULL," +
                "play_date TEXT NOT NULL," +
                "start_time TEXT NOT NULL," +
                "customer_name TEXT NOT NULL," +
                "phone_cipher TEXT," +
                "phone_masked TEXT NOT NULL," +
                "party_size INTEGER NOT NULL," +
                "booking_label TEXT NOT NULL," +
                "source_label TEXT NOT NULL," +
                "total_amount INTEGER NOT NULL," +
                "special_request TEXT NOT NULL," +
                "reservation_status TEXT NOT NULL," +
                "created_at TEXT NOT NULL" +
                ")");
        db.execSQL("CREATE INDEX notifications_created_idx ON notifications(created_at DESC, id DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS notifications");
        onCreate(db);
    }

    boolean insert(OwnerNotification item) {
        return insertInto(getWritableDatabase(), item);
    }

    void insertAll(List<OwnerNotification> items) {
        if (items == null || items.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (OwnerNotification item : items) insertInto(db, item);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private boolean insertInto(SQLiteDatabase db, OwnerNotification item) {
        ContentValues values = new ContentValues();
        values.put("id", item.id);
        values.put("event_type", safe(item.eventType));
        values.put("title", safe(item.title));
        values.put("reservation_id", safe(item.reservationId));
        values.put("theme_title", safe(item.themeTitle));
        values.put("play_date", safe(item.playDate));
        values.put("start_time", safe(item.startTime));
        values.put("customer_name", safe(item.customerName));
        try {
            values.put("phone_cipher", SecurePrefs.encryptLocal(safe(item.phone)));
        } catch (Exception error) {
            values.putNull("phone_cipher");
        }
        values.put("phone_masked", safe(item.phoneMasked));
        values.put("party_size", Math.max(0, item.partySize));
        values.put("booking_label", safe(item.bookingLabel));
        values.put("source_label", safe(item.sourceLabel));
        values.put("total_amount", Math.max(0, item.totalAmount));
        values.put("special_request", safe(item.specialRequest));
        values.put("reservation_status", safe(item.reservationStatus));
        values.put("created_at", safe(item.createdAt));
        return db.insertWithOnConflict(
                "notifications", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    List<OwnerNotification> listRecent(int requestedLimit) {
        int limit = Math.min(AppConfig.LOCAL_HISTORY_LIMIT, Math.max(1, requestedLimit));
        ArrayList<OwnerNotification> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "notifications", null, null, null, null, null, "id DESC", String.valueOf(limit))) {
            while (cursor.moveToNext()) {
                OwnerNotification item = new OwnerNotification();
                item.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
                item.eventType = cursor.getString(cursor.getColumnIndexOrThrow("event_type"));
                item.title = cursor.getString(cursor.getColumnIndexOrThrow("title"));
                item.reservationId = cursor.getString(cursor.getColumnIndexOrThrow("reservation_id"));
                item.themeTitle = cursor.getString(cursor.getColumnIndexOrThrow("theme_title"));
                item.playDate = cursor.getString(cursor.getColumnIndexOrThrow("play_date"));
                item.startTime = cursor.getString(cursor.getColumnIndexOrThrow("start_time"));
                item.customerName = cursor.getString(cursor.getColumnIndexOrThrow("customer_name"));
                String cipher = cursor.getString(cursor.getColumnIndexOrThrow("phone_cipher"));
                item.phoneMasked = cursor.getString(cursor.getColumnIndexOrThrow("phone_masked"));
                try {
                    item.phone = cipher == null ? item.phoneMasked : SecurePrefs.decryptLocal(cipher);
                } catch (Exception error) {
                    item.phone = item.phoneMasked;
                }
                item.partySize = cursor.getInt(cursor.getColumnIndexOrThrow("party_size"));
                item.bookingLabel = cursor.getString(cursor.getColumnIndexOrThrow("booking_label"));
                item.sourceLabel = cursor.getString(cursor.getColumnIndexOrThrow("source_label"));
                item.totalAmount = cursor.getInt(cursor.getColumnIndexOrThrow("total_amount"));
                item.specialRequest = cursor.getString(cursor.getColumnIndexOrThrow("special_request"));
                item.reservationStatus = cursor.getString(cursor.getColumnIndexOrThrow("reservation_status"));
                item.createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"));
                result.add(item);
            }
        }
        return result;
    }

    long maxId() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COALESCE(MAX(id),0) FROM notifications", null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    void trimToMax(int requestedLimit) {
        int limit = Math.min(10_000, Math.max(100, requestedLimit));
        getWritableDatabase().execSQL(
                "DELETE FROM notifications WHERE id NOT IN " +
                        "(SELECT id FROM notifications ORDER BY id DESC LIMIT " + limit + ")"
        );
    }

    void clearAll() {
        getWritableDatabase().delete("notifications", null, null);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

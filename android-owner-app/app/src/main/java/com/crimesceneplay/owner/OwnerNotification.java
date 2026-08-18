package com.crimesceneplay.owner;

import org.json.JSONObject;

final class OwnerNotification {
    long id;
    String eventType;
    String title;
    String reservationId;
    String themeTitle;
    String playDate;
    String startTime;
    String customerName;
    String phone;
    String phoneMasked;
    int partySize;
    String bookingLabel;
    String sourceLabel;
    int totalAmount;
    String specialRequest;
    String reservationStatus;
    String createdAt;

    static OwnerNotification fromJson(JSONObject json) {
        OwnerNotification item = new OwnerNotification();
        item.id = json.optLong("id");
        item.eventType = json.optString("eventType", "NEW_RESERVATION");
        item.title = json.optString("title", "예약 알림");
        item.reservationId = json.optString("reservationId", "");
        item.themeTitle = json.optString("themeTitle", "");
        item.playDate = json.optString("playDate", "");
        item.startTime = json.optString("startTime", "");
        item.customerName = json.optString("customerName", "");
        item.phone = json.optString("phone", "");
        item.phoneMasked = json.optString("phoneMasked", "");
        item.partySize = json.optInt("partySize");
        item.bookingLabel = json.optString("bookingLabel", "");
        item.sourceLabel = json.optString("sourceLabel", "");
        item.totalAmount = json.optInt("totalAmount");
        item.specialRequest = json.optString("specialRequest", "");
        item.reservationStatus = json.optString("reservationStatus", "");
        item.createdAt = json.optString("createdAt", "");
        return item;
    }
}

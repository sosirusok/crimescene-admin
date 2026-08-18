package com.crimesceneplay.owner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (new SecurePrefs(context).isPaired()) NotificationSyncService.start(context);
    }
}

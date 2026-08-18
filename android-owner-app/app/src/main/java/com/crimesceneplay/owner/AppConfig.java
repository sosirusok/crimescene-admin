package com.crimesceneplay.owner;

final class AppConfig {
    static final String API_BASE = "https://jhjbiejqtbidloxcwryr.supabase.co/functions/v1/owner-app-api";
    static final String PUBLISHABLE_KEY = "sb_publishable_mA5DOfPA-ExloawT3aJpNw_2PeVgEEc";
    static final String APP_VERSION = "1.0.0";
    static final int DEFAULT_POLL_SECONDS = 30;
    static final String ACTION_NEW_DATA = "com.crimesceneplay.owner.NEW_DATA";
    static final String ACTION_SYNC_NOW = "com.crimesceneplay.owner.SYNC_NOW";

    private AppConfig() {}
}

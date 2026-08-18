package com.crimesceneplay.owner;

final class AppConfig {
    static final String API_BASE = "https://jhjbiejqtbidloxcwryr.supabase.co/functions/v1/owner-notify";
    static final String PUBLISHABLE_KEY = "sb_publishable_mA5DOfPA-ExloawT3aJpNw_2PeVgEEc";
    static final String APP_VERSION = "1.1.1";
    static final int DEFAULT_POLL_SECONDS = 15;
    static final int LONG_POLL_READ_TIMEOUT_MS = 30_000;
    static final int NETWORK_RETRY_START_MS = 2_000;
    static final int NETWORK_RETRY_MAX_MS = 30_000;
    static final String ACTION_NEW_DATA = "com.crimesceneplay.owner.NEW_DATA";
    static final String ACTION_SYNC_NOW = "com.crimesceneplay.owner.SYNC_NOW";

    private AppConfig() {}
}

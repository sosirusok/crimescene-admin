package com.crimesceneplay.owner;

final class AppConfig {
    static final String API_BASE = "https://jhjbiejqtbidloxcwryr.supabase.co/functions/v1/owner-app-api";
    static final String PUBLISHABLE_KEY = "sb_publishable_mA5DOfPA-ExloawT3aJpNw_2PeVgEEc";
    static final String APP_VERSION = "1.2.0";

    static final int DEFAULT_POLL_SECONDS = 15;
    static final int LONG_POLL_READ_TIMEOUT_MS = 32_000;
    static final int NETWORK_RETRY_START_MS = 2_000;
    static final int NETWORK_RETRY_MAX_MS = 30_000;
    static final int INITIAL_HISTORY_LIMIT = 120;
    static final int INCREMENTAL_LIMIT = 100;
    static final int LOCAL_HISTORY_LIMIT = 2_000;

    static final String ACTION_NEW_DATA = "com.crimesceneplay.owner.NEW_DATA";
    static final String ACTION_SYNC_NOW = "com.crimesceneplay.owner.SYNC_NOW";
    static final String ACTION_SYNC_STATE = "com.crimesceneplay.owner.SYNC_STATE";
    static final String EXTRA_SYNC_STATE = "sync_state";
    static final String EXTRA_SYNC_MESSAGE = "sync_message";

    static final String STATE_CONNECTING = "CONNECTING";
    static final String STATE_CONNECTED = "CONNECTED";
    static final String STATE_SYNCING = "SYNCING";
    static final String STATE_RETRYING = "RETRYING";
    static final String STATE_STOPPED = "STOPPED";

    private AppConfig() {}
}

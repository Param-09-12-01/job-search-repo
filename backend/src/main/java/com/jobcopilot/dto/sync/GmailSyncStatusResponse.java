package com.jobcopilot.dto.sync;

import java.time.LocalDateTime;

public record GmailSyncStatusResponse(
    boolean authorized,
    String authUrl,
    LocalDateTime lastSyncTime,
    GmailSyncResponse lastSyncResult,
    boolean isRunning
) {}

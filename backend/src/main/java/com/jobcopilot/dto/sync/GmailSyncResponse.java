package com.jobcopilot.dto.sync;

import java.time.LocalDateTime;

public record GmailSyncResponse(
    int scanned,
    int detected,
    int added,
    int skipped,
    int errors,
    LocalDateTime syncTime
) {}

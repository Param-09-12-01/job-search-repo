package com.jobcopilot.entity.enums;

/**
 * Supported external job-provider types. Adding a new provider means adding a value here
 * and implementing a corresponding {@code JobSourceAdapter}.
 */
public enum JobSourceType {
    ADZUNA,
    JSEARCH,
    GREENHOUSE,
    LEVER,
    REMOTEOK,
    FINDWORK,
    MANUAL,
    GMAIL_SYNC
}

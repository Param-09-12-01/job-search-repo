package com.jobcopilot.entity.enums;

/**
 * Lifecycle status of an application, aligned with the Kanban board columns.
 */
public enum ApplicationStatus {
    MATCHED,
    SAVED,
    APPLIED,
    VIEWED,
    INTERVIEW,
    OFFER,
    REJECTED,
    GHOSTED_BY_USER,
    GHOSTED_BY_COMPANY
}

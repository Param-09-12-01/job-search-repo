package com.jobcopilot.dto.notification;

import com.jobcopilot.entity.enums.NotificationChannel;
import com.jobcopilot.entity.enums.NotificationStatus;

import java.time.LocalDateTime;

/**
 * Notification representation returned to clients.
 */
public record NotificationResponse(
        Long id,
        NotificationChannel channel,
        String title,
        String message,
        NotificationStatus status,
        String error,
        Long postingId,
        boolean read,
        LocalDateTime createdAt,
        LocalDateTime sentAt
) {
}

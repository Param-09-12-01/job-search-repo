package com.jobcopilot.notification;

import com.jobcopilot.entity.enums.NotificationChannel;

/**
 * Strategy interface for a notification delivery channel. Adding a channel means implementing
 * this interface; the {@code NotificationService} discovers all beans automatically.
 */
public interface NotificationSender {

    /** The channel this sender handles. */
    NotificationChannel channel();

    /** Whether this channel is currently enabled/configured. */
    boolean isEnabled();

    /**
     * Deliver a message. Implementations must throw on failure so the caller can record the error.
     */
    void send(String title, String message);
}

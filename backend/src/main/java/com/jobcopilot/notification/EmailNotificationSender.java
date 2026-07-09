package com.jobcopilot.notification;

import com.jobcopilot.config.AppProperties;
import com.jobcopilot.entity.enums.NotificationChannel;
import com.jobcopilot.exception.IntegrationException;
import com.jobcopilot.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends notifications via SMTP email. Enabled state and recipient are read from runtime settings
 * (admin panel) with a fallback to static configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;
    private final AppProperties properties;
    private final SettingsService settingsService;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public boolean isEnabled() {
        boolean enabled = settingsService.getBooleanValue("notification.email.enabled",
                properties.getNotification().getEmail().isEnabled());
        return enabled && !resolveRecipient().isBlank();
    }

    @Override
    public void send(String title, String message) {
        String to = resolveRecipient();
        if (to.isBlank()) {
            throw new IntegrationException("Email recipient is not configured");
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(properties.getNotification().getEmail().getFrom());
            mail.setTo(to);
            mail.setSubject(title);
            mail.setText(message);
            mailSender.send(mail);
            log.info("Email notification sent to {}", to);
        } catch (Exception e) {
            throw new IntegrationException("Failed to send email notification: " + e.getMessage(), e);
        }
    }

    private String resolveRecipient() {
        String configured = properties.getNotification().getEmail().getTo();
        return settingsService.getValue("notification.email.to", configured == null ? "" : configured);
    }
}

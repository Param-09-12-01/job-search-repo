package com.jobcopilot.notification;

import com.jobcopilot.config.AppProperties;
import com.jobcopilot.entity.enums.NotificationChannel;
import com.jobcopilot.exception.IntegrationException;
import com.jobcopilot.service.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Sends notifications via the Telegram Bot API. Bot token and chat id are read from runtime
 * settings (admin panel) with a fallback to static configuration. Third-party passwords are
 * never stored — only the bot token, which the user provisions themselves.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramNotificationSender implements NotificationSender {

    private final AppProperties properties;
    private final SettingsService settingsService;
    private final RestClient restClient;

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.TELEGRAM;
    }

    @Override
    public boolean isEnabled() {
        boolean enabled = settingsService.getBooleanValue("notification.telegram.enabled",
                properties.getNotification().getTelegram().isEnabled());
        return enabled && !resolveToken().isBlank() && !resolveChatId().isBlank();
    }

    @Override
    public void send(String title, String message) {
        String token = resolveToken();
        String chatId = resolveChatId();
        if (token.isBlank() || chatId.isBlank()) {
            throw new IntegrationException("Telegram bot token or chat id is not configured");
        }
        String url = "%s/bot%s/sendMessage".formatted(
                properties.getNotification().getTelegram().getApiBaseUrl(), token);
        String text = "*%s*%n%n%s".formatted(title, message);
        try {
            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", chatId, "text", text, "parse_mode", "Markdown"))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Telegram notification sent to chat {}", chatId);
        } catch (Exception e) {
            throw new IntegrationException("Failed to send Telegram notification: " + e.getMessage(), e);
        }
    }

    private String resolveToken() {
        String configured = properties.getNotification().getTelegram().getBotToken();
        return settingsService.getValue("notification.telegram.bot-token", configured == null ? "" : configured);
    }

    private String resolveChatId() {
        String configured = properties.getNotification().getTelegram().getChatId();
        return settingsService.getValue("notification.telegram.chat-id", configured == null ? "" : configured);
    }
}

package com.jobcopilot.service;

import com.jobcopilot.dto.settings.SettingsResponse;
import com.jobcopilot.dto.settings.UpdateSettingsRequest;
import com.jobcopilot.entity.AppSetting;
import com.jobcopilot.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Manages runtime key/value settings surfaced in the admin panel.
 * Secret values are masked when read and only overwritten when a non-masked value is submitted.
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final String MASK = "********";

    /** Keys the admin panel is allowed to write. Prevents arbitrary key injection. */
    private static final Set<String> WRITABLE_KEYS = Set.of(
            "scheduler.cron",
            "scheduler.enabled",
            "notification.score-threshold",
            "notification.email.enabled",
            "notification.email.notify-on-match",
            "notification.email.to",
            "notification.telegram.enabled",
            "notification.telegram.bot-token",
            "notification.telegram.chat-id",
            "automation.browser-path",
            "automation.browser-profile-path",
            "profile.resume-path",
            "integration.adzuna.enabled",
            "integration.adzuna.app-id",
            "integration.adzuna.app-key",
            "integration.jsearch.enabled",
            "integration.jsearch.api-key",
            "integration.greenhouse.enabled",
            "integration.greenhouse.boards",
            "integration.lever.enabled",
            "integration.lever.companies",
            "integration.remoteok.enabled",
            "integration.findwork.enabled",
            "integration.findwork.api-key"
    );

    /** Keys whose values must be masked on read. */
    private static final Set<String> SECRET_KEYS = Set.of(
            "notification.telegram.bot-token",
            "integration.adzuna.app-key",
            "integration.jsearch.api-key",
            "integration.findwork.api-key"
    );

    private final AppSettingRepository settingRepository;

    @Transactional(readOnly = true)
    public SettingsResponse getSettings() {
        Map<String, String> values = new LinkedHashMap<>();
        settingRepository.findAll().forEach(s ->
                values.put(s.getKey(), s.isSecret() && hasValue(s.getValue()) ? MASK : s.getValue()));
        return new SettingsResponse(values);
    }

    @Transactional(readOnly = true)
    public String getValue(String key, String defaultValue) {
        return settingRepository.findById(key)
                .map(AppSetting::getValue)
                .filter(this::hasValue)
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public int getIntValue(String key, int defaultValue) {
        String raw = getValue(key, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public boolean getBooleanValue(String key, boolean defaultValue) {
        String raw = getValue(key, null);
        return raw == null ? defaultValue : Boolean.parseBoolean(raw.trim());
    }

    @Transactional
    public SettingsResponse updateSettings(UpdateSettingsRequest request) {
        request.values().forEach((key, value) -> {
            if (!WRITABLE_KEYS.contains(key)) {
                return; // silently ignore non-whitelisted keys
            }
            boolean secret = SECRET_KEYS.contains(key);
            // Don't overwrite a stored secret when the client echoes back the mask.
            if (secret && MASK.equals(value)) {
                return;
            }
            AppSetting setting = settingRepository.findById(key)
                    .orElseGet(() -> AppSetting.builder().key(key).secret(secret).build());
            setting.setValue(value);
            setting.setSecret(secret);
            setting.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            settingRepository.save(setting);
        });
        return getSettings();
    }

    private boolean hasValue(String value) {
        return Optional.ofNullable(value).filter(v -> !v.isBlank()).isPresent();
    }
}

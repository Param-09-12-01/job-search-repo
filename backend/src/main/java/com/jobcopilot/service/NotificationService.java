package com.jobcopilot.service;

import com.jobcopilot.config.AppProperties;
import com.jobcopilot.dto.common.PageResponse;
import com.jobcopilot.dto.notification.NotificationResponse;
import com.jobcopilot.entity.Notification;
import com.jobcopilot.entity.Posting;
import com.jobcopilot.entity.enums.NotificationChannel;
import com.jobcopilot.entity.enums.NotificationStatus;
import com.jobcopilot.exception.ResourceNotFoundException;
import com.jobcopilot.mapper.NotificationMapper;
import com.jobcopilot.notification.NotificationSender;
import com.jobcopilot.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates notification delivery across all enabled channels and persists each attempt.
 * Channels are discovered by injecting every {@link NotificationSender} bean, so adding a channel
 * requires no change here.
 */
@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final SettingsService settingsService;
    private final AppProperties properties;
    private final Map<NotificationChannel, NotificationSender> senders;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationMapper notificationMapper,
                               SettingsService settingsService,
                               AppProperties properties,
                               List<NotificationSender> senderBeans) {
        this.notificationRepository = notificationRepository;
        this.notificationMapper = notificationMapper;
        this.settingsService = settingsService;
        this.properties = properties;
        this.senders = senderBeans.stream()
                .collect(Collectors.toMap(NotificationSender::channel, s -> s));
    }

    /**
     * Dispatch a message to every enabled channel, recording one Notification per channel.
     *
     * @return number of channels that delivered successfully
     */
    @Transactional
    public int dispatch(String title, String message, Posting posting) {
        int delivered = 0;
        for (NotificationSender sender : senders.values()) {
            if (!sender.isEnabled()) {
                continue;
            }
            Notification record = Notification.builder()
                    .channel(sender.channel())
                    .title(title)
                    .message(message)
                    .posting(posting)
                    .status(NotificationStatus.PENDING)
                    .build();
            try {
                sender.send(title, message);
                record.setStatus(NotificationStatus.SENT);
                record.setSentAt(LocalDateTime.now());
                delivered++;
            } catch (Exception e) {
                record.setStatus(NotificationStatus.FAILED);
                record.setError(e.getMessage());
                log.warn("Notification delivery failed on {}: {}", sender.channel(), e.getMessage());
            }
            notificationRepository.save(record);
        }
        return delivered;
    }

    /**
     * Dispatch a high-score job match notification. Email delivery is gated behind the
     * {@code notification.email.notify-on-match} flag; other channels are sent normally.
     *
     * @return number of channels that delivered successfully
     */
    @Transactional
    public int dispatchHighScore(String title, String message, Posting posting) {
        int delivered = 0;
        boolean emailNotifyOnMatch = settingsService.getBooleanValue("notification.email.notify-on-match",
                properties.getNotification().getEmail().isNotifyOnMatch());

        for (NotificationSender sender : senders.values()) {
            if (!sender.isEnabled()) {
                continue;
            }
            if (sender.channel() == NotificationChannel.EMAIL && !emailNotifyOnMatch) {
                log.debug("Email notify-on-match is disabled — skipping email for high-score job");
                continue;
            }
            Notification record = Notification.builder()
                    .channel(sender.channel())
                    .title(title)
                    .message(message)
                    .posting(posting)
                    .status(NotificationStatus.PENDING)
                    .build();
            try {
                sender.send(title, message);
                record.setStatus(NotificationStatus.SENT);
                record.setSentAt(LocalDateTime.now());
                delivered++;
            } catch (Exception e) {
                record.setStatus(NotificationStatus.FAILED);
                record.setError(e.getMessage());
                log.warn("Notification delivery failed on {}: {}", sender.channel(), e.getMessage());
            }
            notificationRepository.save(record);
        }
        return delivered;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getRecent() {
        return notificationRepository.findTop10ByOrderByCreatedAtDesc().stream()
                .map(notificationMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PageResponse.from(
                notificationRepository.findAllByOrderByCreatedAtDesc(pageable),
                notificationMapper::toResponse);
    }

    @Transactional
    public NotificationResponse markRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
        notification.setRead(true);
        return notificationMapper.toResponse(notificationRepository.save(notification));
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return notificationRepository.countByReadFalse();
    }
}

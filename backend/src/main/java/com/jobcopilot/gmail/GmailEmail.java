package com.jobcopilot.gmail;

import java.time.LocalDateTime;
import java.util.List;

public record GmailEmail(
    String messageId,
    String subject,
    String body,
    List<String> recipients,
    LocalDateTime sentDate
) {}

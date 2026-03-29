package com.kova.aiticketinghub.notification.DTO;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID ticketId,
        String message,
        String priority,
        Instant createdAt
) {}
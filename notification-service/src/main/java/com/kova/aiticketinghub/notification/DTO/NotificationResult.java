package com.kova.aiticketinghub.notification.DTO;

import com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketPriority;

import java.time.Instant;
import java.util.UUID;

public record NotificationResult(
        UUID ticketId,
        String message,
        TicketPriority priority,
        Instant createdAt
) {}
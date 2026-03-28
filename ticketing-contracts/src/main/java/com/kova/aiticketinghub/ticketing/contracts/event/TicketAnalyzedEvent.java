package com.kova.aiticketinghub.ticketing.contracts.event;

import com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketCategory;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketPriority;

import java.time.Instant;
import java.util.UUID;

public record TicketAnalyzedEvent(
        UUID ticketId,
        TicketCategory category,
        TicketPriority priority,
        String summary,
        Instant analyzedAt
) { }
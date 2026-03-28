package com.kova.aiticketinghub.ticketing.contracts.event;


import java.time.Instant;
import java.util.UUID;

public record TicketCreatedEvent(
        UUID ticketId,
        String title,
        String description,
        Instant createdAt
) {}
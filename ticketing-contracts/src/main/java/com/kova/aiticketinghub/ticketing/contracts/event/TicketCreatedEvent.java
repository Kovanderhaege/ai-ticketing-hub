package com.kova.aiticketinghub.ticketing.contracts.event;
import java.time.Instant;
import java.util.UUID;

public record TicketCreatedEvent(UUID ticketId, String title, String description, Instant createdAt,
        UUID requestId, String component, String environment, String stepsToReproduce,
        String expectedResult, String actualResult) {
    public TicketCreatedEvent(UUID ticketId, String title, String description, Instant createdAt) {
        this(ticketId, title, description, createdAt, null, null, null, null, null, null);
    }
}
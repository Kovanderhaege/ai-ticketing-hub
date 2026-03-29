package com.kova.aiticketinghub.ticket.DTO;


import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        String title,
        String description,
        String status,
        String category,
        String priority,
        String summary,
        Instant createdAt,
        Instant updatedAt
) {}
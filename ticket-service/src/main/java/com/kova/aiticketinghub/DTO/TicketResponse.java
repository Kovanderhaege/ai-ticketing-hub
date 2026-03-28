package com.kova.aiticketinghub.DTO;


import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        String title,
        String description,
        String status,
        Instant createdAt
) {}
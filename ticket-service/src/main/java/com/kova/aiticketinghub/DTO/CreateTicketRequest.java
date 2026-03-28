package com.kova.aiticketinghub.DTO;

public record CreateTicketRequest(
        String title,
        String description
) {}
package com.kova.aiticketinghub.ticket.DTO;

public record CreateTicketRequest(
        String title,
        String description
) {}
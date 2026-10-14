package com.kova.aiticketinghub.ticket.DTO;

import com.kova.aiticketinghub.ticket.entity.*;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketCategory;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketPriority;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record CreateTicketRequest(
        @Size(max = 255) String title,
        @NotBlank @Size(max = 2000) String description,
        @Size(max = 255) String assignee,
        TicketType type,
        TicketSeverity severity,
        @Size(max = 255) String component,
        @Size(max = 255) String affectedVersion,
        @Size(max = 255) String fixVersion,
        LocalDate dueDate,
        @Size(max = 2000) String environment,
        @Size(max = 5000) String stepsToReproduce,
        @Size(max = 2000) String expectedResult,
        @Size(max = 2000) String actualResult,
        @Size(max = 10000) String rootCause,
        @Size(max = 10000) String technicalSolution,
        @Size(max = 20) List<@NotBlank @Size(max = 50) String> labels,
        TicketStatus status,
        TicketCategory category,
        TicketPriority priority,
        Boolean autoTitle
) {}

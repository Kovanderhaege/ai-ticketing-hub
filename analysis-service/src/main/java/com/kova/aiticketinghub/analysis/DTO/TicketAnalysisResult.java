package com.kova.aiticketinghub.analysis.DTO;

import com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketCategory;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketPriority;

public record TicketAnalysisResult(
        TicketCategory category,
        TicketPriority priority,
        String summary
) {
}
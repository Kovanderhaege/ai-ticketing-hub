package com.kova.aiticketinghub.analysis.DTO;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.*;
public record TicketAnalysisResult(TicketCategory category, TicketPriority priority, String summary,
        TicketSeverity severity, String subject, String assignee) {}
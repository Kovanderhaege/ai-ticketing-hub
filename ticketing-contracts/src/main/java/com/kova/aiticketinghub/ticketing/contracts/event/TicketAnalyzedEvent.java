package com.kova.aiticketinghub.ticketing.contracts.event;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.*;
import java.time.Instant;
import java.util.UUID;

public record TicketAnalyzedEvent(UUID ticketId, TicketCategory category, TicketPriority priority,
        String summary, Instant analyzedAt, UUID requestId, TicketSeverity severity, String subject,
        String assignee, AnalysisStatus analysisStatus, String failureCode, String provider, String model) {
    public TicketAnalyzedEvent(UUID ticketId, TicketCategory category, TicketPriority priority,
                               String summary, Instant analyzedAt) {
        this(ticketId, category, priority, summary, analyzedAt, null, null, null, null,
                AnalysisStatus.SUCCEEDED, null, null, null);
    }
}
package com.kova.aiticketinghub.ticket.messaging;
import com.kova.aiticketinghub.ticket.entity.*;
import com.kova.aiticketinghub.ticket.repository.TicketRepository;
import com.kova.aiticketinghub.ticketing.contracts.event.TicketAnalyzedEvent;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.AnalysisStatus;
import org.slf4j.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.Objects;

@Component
public class TicketAnalyzedConsumer {
    private static final Logger log = LoggerFactory.getLogger(TicketAnalyzedConsumer.class);
    private final TicketRepository repository;
    private final TicketAnalysisAppliedProducer appliedProducer;
    public TicketAnalyzedConsumer(TicketRepository repository, TicketAnalysisAppliedProducer appliedProducer) {
        this.repository = repository;
        this.appliedProducer = appliedProducer;
    }

    @KafkaListener(topics = "ticket.analyzed.v1", groupId = "ticket-service")
    @Transactional
    public void consume(TicketAnalyzedEvent event) {
        Ticket ticket = repository.findForUpdateById(event.ticketId())
                .orElseThrow(() -> new IllegalStateException("Ticket not found for analyzed event"));
        if (!Objects.equals(event.requestId(), ticket.getAnalysisRequestId())) return;
        if (event.requestId() != null && ticket.getAnalysisStatus() != AnalysisStatus.PENDING) return;
        if (event.analysisStatus() == AnalysisStatus.PENDING) throw new IllegalArgumentException("Analysis result must be final");
        if (event.analysisStatus() == AnalysisStatus.DISABLED) {
            ticket.setAnalysisStatus(AnalysisStatus.DISABLED);
            ticket.setAnalysisFailureCode(null);
        } else if (event.analysisStatus() == AnalysisStatus.FAILED) {
            ticket.setAnalysisStatus(AnalysisStatus.FAILED);
            ticket.setAnalysisFailureCode(event.failureCode());
        } else {
            if (event.category() == null || event.priority() == null || event.summary() == null)
                throw new IllegalArgumentException("Incomplete analysis event");
            var manual = ticket.getManualAnalysisFields();
            boolean tracked = ticket.isAnalysisFieldsTracked();
            if (tracked ? !manual.contains("category") : ticket.getCategory() == null) ticket.setCategory(event.category());
            if (tracked ? !manual.contains("priority") : ticket.getPriority() == null) ticket.setPriority(event.priority());
            if (event.requestId() != null) {
                if (event.severity() == null || event.subject() == null || event.subject().isBlank()
                        || event.subject().length() > 255 || event.summary().isBlank() || event.summary().length() > 500)
                    throw new IllegalArgumentException("Incomplete analysis event");
                if (!manual.contains("severity")) ticket.setSeverity(TicketSeverity.valueOf(event.severity().name()));
                if (!manual.contains("title")) ticket.setTitle(event.subject());
                if (!manual.contains("assignee")) ticket.setAssignee(event.assignee());
            }
            ticket.setSummary(event.summary());
            ticket.setAnalysisStatus(AnalysisStatus.SUCCEEDED);
            ticket.setAnalysisFailureCode(null);
            if (ticket.getStatus() == TicketStatus.NEW) ticket.setStatus(TicketStatus.ANALYZED);
        }
        ticket.setAnalysisProvider(event.provider());
        ticket.setAnalysisModel(event.model());
        ticket.setUpdatedAt(Instant.now());
        repository.save(ticket);
        if (ticket.getAnalysisStatus() == AnalysisStatus.SUCCEEDED) {
            var applied = new TicketAnalyzedEvent(ticket.getId(), ticket.getCategory(), ticket.getPriority(), ticket.getSummary(),
                    event.analyzedAt(), ticket.getAnalysisRequestId(),
                    ticket.getSeverity() == null ? null : com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketSeverity.valueOf(ticket.getSeverity().name()),
                    ticket.getTitle(), ticket.getAssignee(), AnalysisStatus.SUCCEEDED, null, event.provider(), event.model());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { appliedProducer.send(applied); }
            });
        }
        log.debug("Applied analysis for ticket {}: {}", ticket.getId(), ticket.getAnalysisStatus());
    }
}

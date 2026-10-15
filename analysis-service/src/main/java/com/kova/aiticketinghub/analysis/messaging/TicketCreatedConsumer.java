package com.kova.aiticketinghub.analysis.messaging;
import com.kova.aiticketinghub.analysis.llm.LlmException;
import com.kova.aiticketinghub.analysis.service.TicketAnalysisService;
import com.kova.aiticketinghub.ticketing.contracts.event.*;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.AnalysisStatus;
import org.slf4j.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class TicketCreatedConsumer {
    private static final Logger log = LoggerFactory.getLogger(TicketCreatedConsumer.class);
    private final TicketAnalysisService service;
    private final TicketAnalyzedProducer producer;
    public TicketCreatedConsumer(TicketAnalysisService service, TicketAnalyzedProducer producer) {
        this.service = service;
        this.producer = producer;
    }
    @KafkaListener(topics = "ticket.created.v1", groupId = "analysis-service")
    public void consume(TicketCreatedEvent event) {
        TicketAnalyzedEvent analyzed;
        try {
            var result = service.analyze(event);
            analyzed = new TicketAnalyzedEvent(event.ticketId(), result.category(), result.priority(), result.summary(),
                    Instant.now(), event.requestId(), result.severity(), result.subject(), result.assignee(),
                    AnalysisStatus.SUCCEEDED, null, result.provider(), result.model());
        } catch (LlmException exception) {
            if ("ANALYSIS_DISABLED".equals(exception.code())) log.debug("Analysis disabled for ticket {}", event.ticketId());
            else log.warn("Analysis failed for ticket {}: {}", event.ticketId(), exception.code());
            analyzed = new TicketAnalyzedEvent(event.ticketId(), null, null, null, Instant.now(), event.requestId(),
                    null, null, null, "ANALYSIS_DISABLED".equals(exception.code()) ? AnalysisStatus.DISABLED : AnalysisStatus.FAILED,
                    exception.code(), exception.provider(), exception.model());
        }
        producer.send(analyzed);
    }
}

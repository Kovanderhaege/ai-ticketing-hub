package com.kova.aiticketinghub.analysis.messaging;
import com.kova.aiticketinghub.analysis.config.AnalysisProperties;
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
    private final AnalysisProperties config;
    public TicketCreatedConsumer(TicketAnalysisService service, TicketAnalyzedProducer producer, AnalysisProperties config) {
        this.service = service;
        this.producer = producer;
        this.config = config;
    }
    @KafkaListener(topics = "ticket.created.v1", groupId = "analysis-service")
    public void consume(TicketCreatedEvent event) {
        TicketAnalyzedEvent analyzed;
        try {
            var result = service.analyze(event);
            analyzed = new TicketAnalyzedEvent(event.ticketId(), result.category(), result.priority(), result.summary(),
                    Instant.now(), event.requestId(), result.severity(), result.subject(), result.assignee(),
                    AnalysisStatus.SUCCEEDED, null, config.getProvider().name(), config.getModel());
        } catch (LlmException exception) {
            log.warn("Analysis failed for ticket {}: {}", event.ticketId(), exception.code());
            analyzed = new TicketAnalyzedEvent(event.ticketId(), null, null, null, Instant.now(), event.requestId(),
                    null, null, null, AnalysisStatus.FAILED, exception.code(), config.getProvider().name(), config.getModel());
        }
        producer.send(analyzed);
    }
}
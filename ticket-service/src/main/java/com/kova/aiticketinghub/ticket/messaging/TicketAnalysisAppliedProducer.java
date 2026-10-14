package com.kova.aiticketinghub.ticket.messaging;

import com.kova.aiticketinghub.ticketing.contracts.event.TicketAnalyzedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TicketAnalysisAppliedProducer {
    private static final Logger log = LoggerFactory.getLogger(TicketAnalysisAppliedProducer.class);
    private final KafkaTemplate<String, Object> kafka;
    public TicketAnalysisAppliedProducer(KafkaTemplate<String, Object> kafka) { this.kafka = kafka; }
    public void send(TicketAnalyzedEvent event) {
        kafka.send("ticket.analysis-applied.v1", event.ticketId().toString(), event)
                .whenComplete((result, failure) -> {
                    if (failure != null) log.error("Unable to publish applied analysis for ticket {}", event.ticketId());
                });
    }
}
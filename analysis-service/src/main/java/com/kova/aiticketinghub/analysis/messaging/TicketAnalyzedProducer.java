package com.kova.aiticketinghub.analysis.messaging;

import com.kova.aiticketinghub.ticketing.contracts.event.TicketAnalyzedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TicketAnalyzedProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TicketAnalyzedProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(TicketAnalyzedEvent event) {
        kafkaTemplate.send("ticket.analyzed.v1", event.ticketId().toString(), event);
    }
}
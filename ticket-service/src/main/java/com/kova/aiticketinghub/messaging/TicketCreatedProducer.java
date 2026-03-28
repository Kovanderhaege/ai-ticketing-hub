package com.kova.aiticketinghub.messaging;

import com.kova.aiticketinghub.messaging.event.TicketCreatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TicketCreatedProducer {

    private final KafkaTemplate<String, TicketCreatedEvent> kafkaTemplate;

    public TicketCreatedProducer(KafkaTemplate<String, TicketCreatedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(TicketCreatedEvent event) {
        kafkaTemplate.send("ticket.created.v1", event.ticketId().toString(), event);
    }
}
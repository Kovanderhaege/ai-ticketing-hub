package com.kova.aiticketinghub.ticket.messaging;

import com.kova.aiticketinghub.ticket.entity.Ticket;
import com.kova.aiticketinghub.ticket.entity.TicketStatus;
import com.kova.aiticketinghub.ticket.repository.TicketRepository;
import com.kova.aiticketinghub.ticketing.contracts.event.TicketAnalyzedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TicketAnalyzedConsumer {

    private final TicketRepository repository;

    public TicketAnalyzedConsumer(TicketRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "ticket.analyzed.v1", groupId = "ticket-service")
    public void consume(TicketAnalyzedEvent event) {
        Ticket ticket = repository.findById(event.ticketId())
                .orElseThrow(() -> new IllegalStateException(
                        "Ticket not found for analyzed event: " + event.ticketId()
                ));

        ticket.setCategory(event.category());
        ticket.setPriority(event.priority());
        ticket.setSummary(event.summary());
        ticket.setStatus(TicketStatus.ANALYZED);
        ticket.setUpdatedAt(Instant.now());

        repository.save(ticket);

        System.out.println("Updated ticket after analysis: " + ticket.getId());
    }
}
package com.kova.aiticketinghub.service;

import com.kova.aiticketinghub.DTO.CreateTicketRequest;
import com.kova.aiticketinghub.DTO.TicketResponse;
import com.kova.aiticketinghub.entity.Ticket;
import com.kova.aiticketinghub.entity.TicketStatus;
import com.kova.aiticketinghub.messaging.TicketCreatedProducer;
import com.kova.aiticketinghub.messaging.event.TicketCreatedEvent;
import com.kova.aiticketinghub.repository.TicketRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class TicketService {

    private final TicketRepository repository;
    private final TicketCreatedProducer producer;

    public TicketService(TicketRepository repository, TicketCreatedProducer producer) {
        this.repository = repository;
        this.producer = producer;
    }

    public TicketResponse create(CreateTicketRequest request) {
        Ticket ticket = new Ticket();
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setStatus(TicketStatus.NEW);
        ticket.setCreatedAt(Instant.now());
        ticket.setUpdatedAt(Instant.now());

        Ticket saved = repository.save(ticket);

        producer.send(new TicketCreatedEvent(
                saved.getId(),
                saved.getTitle(),
                saved.getDescription(),
                saved.getCreatedAt()
        ));

        return new TicketResponse(
                saved.getId(),
                saved.getTitle(),
                saved.getDescription(),
                saved.getStatus().name(),
                saved.getCreatedAt()
        );
    }
}
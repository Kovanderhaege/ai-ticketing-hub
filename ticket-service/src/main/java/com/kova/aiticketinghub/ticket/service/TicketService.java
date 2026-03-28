package com.kova.aiticketinghub.ticket.service;

import com.kova.aiticketinghub.ticket.DTO.CreateTicketRequest;
import com.kova.aiticketinghub.ticket.DTO.TicketResponse;
import com.kova.aiticketinghub.ticket.entity.Ticket;
import com.kova.aiticketinghub.ticket.entity.TicketStatus;
import com.kova.aiticketinghub.ticket.messaging.TicketCreatedProducer;
import com.kova.aiticketinghub.ticket.repository.TicketRepository;
import com.kova.aiticketinghub.ticketing.contracts.event.TicketCreatedEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    public List<TicketResponse> getAll() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public TicketResponse getById(UUID id) {
        Ticket t = repository.findById(id)
                .orElseThrow();
        return toResponse(t);
    }

    private TicketResponse toResponse(Ticket t) {
        return new TicketResponse(
                t.getId(),
                t.getTitle(),
                t.getDescription(),
                t.getStatus().name(),
                t.getCreatedAt()
        );
    }
}
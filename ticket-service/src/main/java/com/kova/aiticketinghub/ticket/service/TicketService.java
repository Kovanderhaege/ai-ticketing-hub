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

        Ticket savedTicket = repository.save(ticket);

        producer.send(new TicketCreatedEvent(
                savedTicket.getId(),
                savedTicket.getTitle(),
                savedTicket.getDescription(),
                savedTicket.getCreatedAt()
        ));

        return toResponse(savedTicket);
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

    private TicketResponse toResponse(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus() != null ? ticket.getStatus().name() : null,
                ticket.getCategory() != null ? ticket.getCategory().name() : null,
                ticket.getPriority() != null ? ticket.getPriority().name() : null,
                ticket.getSummary(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
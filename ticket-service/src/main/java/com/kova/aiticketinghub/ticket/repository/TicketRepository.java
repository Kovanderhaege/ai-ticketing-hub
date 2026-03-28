package com.kova.aiticketinghub.ticket.repository;

import com.kova.aiticketinghub.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
}
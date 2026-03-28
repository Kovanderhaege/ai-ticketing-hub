package com.kova.aiticketinghub.repository;

import com.kova.aiticketinghub.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
}
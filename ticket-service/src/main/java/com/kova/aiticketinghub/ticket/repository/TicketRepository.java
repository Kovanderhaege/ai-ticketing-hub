package com.kova.aiticketinghub.ticket.repository;

import com.kova.aiticketinghub.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from Ticket ticket where ticket.id = :id")
    Optional<Ticket> findForUpdateById(UUID id);
}

package com.kova.aiticketinghub.ticket.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter @Setter
public class Ticket {
    @Id
    @GeneratedValue
    private UUID id;

    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    private TicketStatus status;

    private Instant createdAt;

    private Instant updatedAt;

}

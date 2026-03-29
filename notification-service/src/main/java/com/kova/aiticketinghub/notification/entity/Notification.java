package com.kova.aiticketinghub.notification.entity;

import com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketPriority;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter @Setter
public class Notification {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID ticketId;

    private String message;

    @Enumerated(EnumType.STRING)
    private TicketPriority priority;

    private Instant createdAt;
}
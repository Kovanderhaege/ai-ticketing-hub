package com.kova.aiticketinghub.ticket.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter @Setter
public class TicketAttachment {
    @Id @GeneratedValue
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;
    @Column(nullable = false)
    private String filename;
    private String contentType;
    private long size;
    private Instant createdAt;
    private String uploadedBy;
    // Hibernate maps byte[] to bytea on PostgreSQL (no large-object/OID dependency).
    @Column(nullable = false)
    private byte[] data;
}

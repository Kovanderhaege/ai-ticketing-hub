package com.kova.aiticketinghub.ticket.entity;

import com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketCategory;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketPriority;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.AnalysisStatus;

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

    @Enumerated(EnumType.STRING)
    private TicketCategory category;

    @Enumerated(EnumType.STRING)
    private TicketPriority priority;

    @Column(length = 500)
    private String summary;

    private String reporter;
    private String assignee;
    private String component;
    private String affectedVersion;
    private String fixVersion;
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    private TicketType type;

    @Enumerated(EnumType.STRING)
    private TicketSeverity severity;

    @Column(length = 2000)
    private String environment;
    @Column(length = 5000)
    private String stepsToReproduce;
    @Column(length = 2000)
    private String expectedResult;
    @Column(length = 2000)
    private String actualResult;
    @Column(length = 10000)
    private String rootCause;
    @Column(length = 10000)
    private String technicalSolution;

    @ElementCollection
    @CollectionTable(name = "ticket_labels", joinColumns = @JoinColumn(name = "ticket_id"))
    @Column(name = "label", length = 50)
    @OrderColumn(name = "label_order")
    private List<String> labels = new ArrayList<>();

    private Instant createdAt;

    private Instant updatedAt;

    private UUID analysisRequestId;
    @Enumerated(EnumType.STRING)
    private AnalysisStatus analysisStatus;
    private String analysisFailureCode;
    private String analysisProvider;
    private String analysisModel;
    @org.hibernate.annotations.ColumnDefault("false")
    @Column(nullable = false)
    private boolean analysisFieldsTracked;

    @ElementCollection
    @CollectionTable(name = "ticket_manual_analysis_fields", joinColumns = @JoinColumn(name = "ticket_id"))
    @Column(name = "field_name", length = 32)
    private Set<String> manualAnalysisFields = new HashSet<>();
}

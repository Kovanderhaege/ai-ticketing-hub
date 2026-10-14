package com.kova.aiticketinghub.ticket.service;

import com.kova.aiticketinghub.ticket.DTO.CreateTicketRequest;
import com.kova.aiticketinghub.ticket.DTO.TicketResponse;
import com.kova.aiticketinghub.ticket.entity.Ticket;
import com.kova.aiticketinghub.ticket.entity.TicketStatus;
import com.kova.aiticketinghub.ticket.entity.TicketType;
import com.kova.aiticketinghub.ticket.entity.TicketSeverity;
import com.kova.aiticketinghub.ticket.messaging.TicketCreatedProducer;
import com.kova.aiticketinghub.ticket.repository.TicketRepository;
import com.kova.aiticketinghub.ticketing.contracts.event.TicketCreatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import java.util.ArrayList;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.AnalysisStatus;

@Service
@Transactional(readOnly = true)
public class TicketService {

    private final TicketRepository repository;
    private final TicketCreatedProducer producer;
    private final AttachmentService attachments;

    public TicketService(TicketRepository repository, TicketCreatedProducer producer, AttachmentService attachments) {
        this.repository = repository;
        this.producer = producer;
        this.attachments = attachments;
    }

    @Transactional
    public TicketResponse create(CreateTicketRequest request, String reporter, List<MultipartFile> files) {
        Ticket ticket = new Ticket();
        validateTitle(request, true);
        applyDetails(ticket, request);
        if (clean(ticket.getTitle()) == null) ticket.setTitle("Analyse en cours…");
        ticket.setAnalysisFieldsTracked(true);
        if (!Boolean.TRUE.equals(request.autoTitle())) ticket.getManualAnalysisFields().add("title");
        if (ticket.getSeverity() != null) ticket.getManualAnalysisFields().add("severity");
        if (ticket.getAssignee() != null) ticket.getManualAnalysisFields().add("assignee");
        ticket.setCategory(request.category());
        ticket.setPriority(request.priority());
        if (request.category() != null) ticket.getManualAnalysisFields().add("category");
        if (request.priority() != null) ticket.getManualAnalysisFields().add("priority");
        ticket.setReporter(reporter);
        ticket.setStatus(TicketStatus.NEW);
        ticket.setCreatedAt(Instant.now());
        ticket.setUpdatedAt(Instant.now());

        Ticket savedTicket = repository.save(ticket);
        attachments.store(savedTicket, files, reporter);

        requestAnalysis(savedTicket);

        return toResponse(savedTicket);
    }

    public List<TicketResponse> getAll() {
        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public TicketResponse getById(UUID id) {
        Ticket t = findTicket(id);
        return toResponse(t);
    }

    @Transactional
    public TicketResponse update(UUID id, CreateTicketRequest request) {
        validateTitle(request, false);
        Ticket ticket = findTicketForUpdate(id);
        initializeManualFields(ticket);
        boolean contentChanged = !Objects.equals(ticket.getTitle(), clean(request.title()))
                || !Objects.equals(ticket.getDescription(), clean(request.description()))
                || !Objects.equals(ticket.getComponent(), clean(request.component()))
                || !Objects.equals(ticket.getEnvironment(), clean(request.environment()))
                || !Objects.equals(ticket.getStepsToReproduce(), clean(request.stepsToReproduce()))
                || !Objects.equals(ticket.getExpectedResult(), clean(request.expectedResult()))
                || !Objects.equals(ticket.getActualResult(), clean(request.actualResult()));
        trackChange(ticket, "title", ticket.getTitle(), clean(request.title()));
        trackChange(ticket, "severity", ticket.getSeverity(), request.severity());
        trackChange(ticket, "assignee", ticket.getAssignee(), clean(request.assignee()));
        if (request.category() != null) trackChange(ticket, "category", ticket.getCategory(), request.category());
        if (request.priority() != null) trackChange(ticket, "priority", ticket.getPriority(), request.priority());
        applyDetails(ticket, request);
        if (request.status() != null) ticket.setStatus(request.status());
        if (request.category() != null) ticket.setCategory(request.category());
        if (request.priority() != null) ticket.setPriority(request.priority());
        ticket.setUpdatedAt(Instant.now());
        if (contentChanged) requestAnalysis(ticket);
        return toResponse(repository.save(ticket));
    }

    @Transactional
    public TicketResponse reanalyze(UUID id) {
        Ticket ticket = findTicketForUpdate(id);
        initializeManualFields(ticket);
        requestAnalysis(ticket);
        ticket.setUpdatedAt(Instant.now());
        return toResponse(repository.save(ticket));
    }

    private Ticket findTicketForUpdate(UUID id) {
        return repository.findForUpdateById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket introuvable."));
    }

    private void validateTitle(CreateTicketRequest request, boolean creating) {
        if (clean(request.title()) == null && !(creating && Boolean.TRUE.equals(request.autoTitle())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Renseignez un titre ou activez sa génération automatique.");
    }

    private void initializeManualFields(Ticket ticket) {
        if (ticket.isAnalysisFieldsTracked()) return;
        if (ticket.getTitle() != null) ticket.getManualAnalysisFields().add("title");
        if (ticket.getSeverity() != null) ticket.getManualAnalysisFields().add("severity");
        if (ticket.getAssignee() != null) ticket.getManualAnalysisFields().add("assignee");
        if (ticket.getCategory() != null) ticket.getManualAnalysisFields().add("category");
        if (ticket.getPriority() != null) ticket.getManualAnalysisFields().add("priority");
        ticket.setAnalysisFieldsTracked(true);
    }

    private void trackChange(Ticket ticket, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            if (after == null) ticket.getManualAnalysisFields().remove(field);
            else ticket.getManualAnalysisFields().add(field);
        }
    }

    private void requestAnalysis(Ticket ticket) {
        ticket.setAnalysisRequestId(UUID.randomUUID());
        ticket.setAnalysisStatus(AnalysisStatus.PENDING);
        ticket.setAnalysisFailureCode(null);
        TicketCreatedEvent event = new TicketCreatedEvent(ticket.getId(), ticket.getTitle(), ticket.getDescription(),
                ticket.getCreatedAt(), ticket.getAnalysisRequestId(), ticket.getComponent(), ticket.getEnvironment(),
                ticket.getStepsToReproduce(), ticket.getExpectedResult(), ticket.getActualResult());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { producer.send(event); }
        });
    }

    private Ticket findTicket(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket introuvable."));
    }

    private void applyDetails(Ticket ticket, CreateTicketRequest request) {
        ticket.setTitle(clean(request.title()));
        ticket.setDescription(request.description().strip());
        ticket.setAssignee(clean(request.assignee()));
        ticket.setType(request.type() != null ? request.type() : TicketType.SUPPORT);
        ticket.setSeverity(request.severity());
        ticket.setComponent(clean(request.component()));
        ticket.setAffectedVersion(clean(request.affectedVersion()));
        ticket.setFixVersion(clean(request.fixVersion()));
        ticket.setDueDate(request.dueDate());
        ticket.setEnvironment(clean(request.environment()));
        ticket.setStepsToReproduce(clean(request.stepsToReproduce()));
        ticket.setExpectedResult(clean(request.expectedResult()));
        ticket.setActualResult(clean(request.actualResult()));
        ticket.setRootCause(clean(request.rootCause()));
        ticket.setTechnicalSolution(clean(request.technicalSolution()));
        ticket.setLabels(new ArrayList<>(request.labels() == null ? List.of() : request.labels().stream().map(String::strip).distinct().toList()));
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
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
                ticket.getUpdatedAt(),
                ticket.getReporter(), ticket.getAssignee(),
                ticket.getType() == null ? null : ticket.getType().name(),
                ticket.getSeverity() == null ? null : ticket.getSeverity().name(),
                ticket.getComponent(), ticket.getAffectedVersion(), ticket.getFixVersion(), ticket.getDueDate(),
                ticket.getEnvironment(), ticket.getStepsToReproduce(), ticket.getExpectedResult(), ticket.getActualResult(),
                ticket.getRootCause(), ticket.getTechnicalSolution(), List.copyOf(ticket.getLabels()),
                attachments.list(ticket.getId()),
                ticket.getAnalysisStatus() == null ? null : ticket.getAnalysisStatus().name(),
                ticket.getAnalysisFailureCode(), ticket.getAnalysisProvider(), ticket.getAnalysisModel()
        );
    }
}

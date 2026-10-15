package com.kova.aiticketinghub.ticket;

import com.kova.aiticketinghub.ticket.entity.*;
import com.kova.aiticketinghub.ticket.messaging.TicketCreatedProducer;
import com.kova.aiticketinghub.ticket.messaging.TicketAnalyzedConsumer;
import com.kova.aiticketinghub.ticket.messaging.TicketAnalysisAppliedProducer;
import com.kova.aiticketinghub.ticket.repository.TicketRepository;
import com.kova.aiticketinghub.ticket.repository.AttachmentRepository;
import com.kova.aiticketinghub.ticketing.contracts.event.TicketAnalyzedEvent;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TicketWorkflowTests {
    @Autowired MockMvc mvc;
    @Autowired WebApplicationContext context;
    @Autowired TicketRepository tickets;
    @Autowired AttachmentRepository attachments;
    @Autowired TicketAnalyzedConsumer consumer;
    @MockitoBean TicketCreatedProducer producer;
    @MockitoBean TicketAnalysisAppliedProducer appliedProducer;

    private static final String DETAILS = """
        {"title":"Connexion impossible","description":"Erreur 500", "reporter":"forged@example.com",
         "assignee":"bob@example.com","type":"BUG","severity":"CRITICAL","component":"Auth",
         "affectedVersion":"1.2","fixVersion":"1.3","dueDate":"2026-11-01","environment":"Firefox / Linux",
         "stepsToReproduce":"1. Se connecter","expectedResult":"Session ouverte","actualResult":"Erreur 500",
         "rootCause":"Jeton expiré","technicalSolution":"Renouveler le jeton","labels":[" production ","régression","production"]}
        """;

    @BeforeEach
    void clear() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
                .defaultRequest(get("/").with(user("alice@example.com"))).build();
        attachments.deleteAll(); tickets.deleteAll(); reset(producer, appliedProducer);
    }

    UUID create() throws Exception {
        mvc.perform(post("/ticket").with(csrf()).contentType("application/json").content(DETAILS))
                .andExpect(status().isOk());
        return tickets.findAll().getFirst().getId();
    }

    @Test
    void detailsAndLabelsPersistAndReporterComesFromSession() throws Exception {
        UUID id = create();
        mvc.perform(get("/ticket/" + id))
                .andExpect(jsonPath("reporter").value("alice@example.com"))
                .andExpect(jsonPath("assignee").value("bob@example.com"))
                .andExpect(jsonPath("type").value("BUG"))
                .andExpect(jsonPath("severity").value("CRITICAL"))
                .andExpect(jsonPath("component").value("Auth"))
                .andExpect(jsonPath("affectedVersion").value("1.2"))
                .andExpect(jsonPath("fixVersion").value("1.3"))
                .andExpect(jsonPath("dueDate").value("2026-11-01"))
                .andExpect(jsonPath("environment").value("Firefox / Linux"))
                .andExpect(jsonPath("stepsToReproduce").value("1. Se connecter"))
                .andExpect(jsonPath("expectedResult").value("Session ouverte"))
                .andExpect(jsonPath("actualResult").value("Erreur 500"))
                .andExpect(jsonPath("rootCause").value("Jeton expiré"))
                .andExpect(jsonPath("technicalSolution").value("Renouveler le jeton"))
                .andExpect(jsonPath("labels.length()").value(2))
                .andExpect(jsonPath("labels[0]").value("production"));
        mvc.perform(get("/ticket")).andExpect(jsonPath("$[0].rootCause").value("Jeton expiré"));
        verify(producer).send(argThat(event -> event.ticketId().equals(id) && tickets.existsById(id)));
    }

    @Test
    void disabledAnalysisEndsThePendingStateWithoutReplacingManualFieldsOrNotifying() throws Exception {
        UUID id = create();
        var pending = tickets.findById(id).orElseThrow();
        consumer.consume(new TicketAnalyzedEvent(id, null, null, null, Instant.now(), pending.getAnalysisRequestId(),
            null, null, null, AnalysisStatus.DISABLED, "ANALYSIS_DISABLED", "OLLAMA", ""));
        mvc.perform(get("/ticket/" + id)).andExpect(status().isOk())
            .andExpect(jsonPath("analysisStatus").value("DISABLED"))
            .andExpect(jsonPath("analysisFailureCode").isEmpty())
            .andExpect(jsonPath("assignee").value("bob@example.com"))
            .andExpect(jsonPath("severity").value("CRITICAL"))
            .andExpect(jsonPath("title").value("Connexion impossible"));
        verifyNoInteractions(appliedProducer);
    }

    @Test
    void editPersistsAndLateAnalysisPreservesManualClassificationAndStatus() throws Exception {
        UUID id = create();
        String edited = DETAILS.replace("\"labels\":", "\"status\":\"RESOLVED\",\"category\":\"ACCOUNT\",\"priority\":\"HIGH\",\"labels\":")
                .replace("Renouveler le jeton", "Correctif déployé");
        mvc.perform(put("/ticket/" + id).with(csrf()).contentType("application/json").content(edited))
                .andExpect(status().isOk()).andExpect(jsonPath("reporter").value("alice@example.com"));
        consumer.consume(analyzed(id, "Résumé IA"));
        mvc.perform(get("/ticket/" + id))
                .andExpect(jsonPath("status").value("RESOLVED"))
                .andExpect(jsonPath("category").value("ACCOUNT"))
                .andExpect(jsonPath("priority").value("HIGH"))
                .andExpect(jsonPath("technicalSolution").value("Correctif déployé"))
                .andExpect(jsonPath("labels.length()").value(2))
                .andExpect(jsonPath("summary").value("Résumé IA"));
        verify(appliedProducer).send(argThat(event -> event.priority() == TicketPriority.HIGH && event.category() == TicketCategory.ACCOUNT));
        verify(producer, times(1)).send(any());
    }

    @Test
    void oldJsonCreationRemainsCompatibleAndCanClearOptionalFields() throws Exception {
        mvc.perform(post("/ticket").with(csrf()).contentType("application/json").content("{\"title\":\"Legacy\",\"description\":\"Details\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("type").value("SUPPORT"))
                .andExpect(jsonPath("labels").isEmpty()).andExpect(jsonPath("attachments").isEmpty());
        UUID id = tickets.findAll().getFirst().getId();
        mvc.perform(put("/ticket/" + id).with(csrf()).contentType("application/json").content(DETAILS)).andExpect(status().isOk());
        mvc.perform(put("/ticket/" + id).with(csrf()).contentType("application/json").content("{\"title\":\"Legacy\",\"description\":\"Details\",\"labels\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("rootCause").isEmpty()).andExpect(jsonPath("labels").isEmpty());
    }

    @Test
    void multipartCreationStoresFilesAndDownloadsExactBytes() throws Exception {
        byte[] bytes = "échec de connexion\ntrace".getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/ticket").file(new MockMultipartFile("ticket", "", "application/json", DETAILS.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("files", "../../logs-échec.txt", "text/plain", bytes))
                .file(new MockMultipartFile("files", "screen.png", "image/png", new byte[]{1, 2, 3})).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("attachments.length()").value(2))
                .andExpect(jsonPath("attachments[0].data").doesNotExist());
        UUID id = tickets.findAll().getFirst().getId();
        var log = attachments.listByTicketId(id).stream().filter(file -> file.filename().endsWith(".txt")).findFirst().orElseThrow();
        assertThat(log.filename()).isEqualTo("logs-échec.txt");
        assertThat(log.uploadedBy()).isEqualTo("alice@example.com");
        mvc.perform(get("/ticket/" + id + "/attachments/" + log.id()))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes))
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));
    }

    @Test
    void addDeleteAndTicketScoping() throws Exception {
        UUID id = create();
        mvc.perform(multipart("/ticket/" + id + "/attachments").file(new MockMultipartFile("files", "error.log", "text/plain", new byte[]{42})).with(csrf()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].filename").value("error.log"));
        UUID fileId = attachments.listByTicketId(id).getFirst().id();
        mvc.perform(get("/ticket/" + UUID.randomUUID() + "/attachments/" + fileId)).andExpect(status().isNotFound());
        mvc.perform(delete("/ticket/" + UUID.randomUUID() + "/attachments/" + fileId).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/ticket/" + id + "/attachments/" + fileId)).andExpect(status().isForbidden());
        mvc.perform(delete("/ticket/" + id + "/attachments/" + fileId).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/ticket/" + id + "/attachments/" + fileId)).andExpect(status().isNotFound());
        assertThat(attachments.count()).isZero();
    }

    @Test
    void invalidFileBatchRollsBackEntireCreationAndDoesNotPublishEvent() throws Exception {
        mvc.perform(multipart("/ticket").file(new MockMultipartFile("ticket", "", "application/json", DETAILS.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("files", "ok.log", "text/plain", new byte[]{1}))
                .file(new MockMultipartFile("files", "empty.log", "text/plain", new byte[0])).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(tickets.count()).isZero();
        assertThat(attachments.count()).isZero();
        verifyNoInteractions(producer);
    }

    @Test
    void oversizedFilesAndInvalidFieldsAreRejected() throws Exception {
        UUID id = create();
        mvc.perform(multipart("/ticket/" + id + "/attachments").file(new MockMultipartFile("files", "big.log", "text/plain", new byte[10 * 1024 * 1024 + 1])).with(csrf()))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/ticket").with(csrf()).contentType("application/json").content(DETAILS.replace("Connexion impossible", " ")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/ticket").with(csrf()).contentType("application/json").content(DETAILS.replace("régression", "x".repeat(51))))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/ticket/" + UUID.randomUUID()).with(csrf()).contentType("application/json").content(DETAILS))
                .andExpect(status().isNotFound());
        assertThat(attachments.count()).isZero();
    }

    @Test
    void attachmentEndpointsRequireAuthenticationAndCsrf() throws Exception {
        UUID id = create();
        String path = "/ticket/" + id + "/attachments";
        mvc.perform(get(path + "/" + UUID.randomUUID()).with(anonymous())).andExpect(status().isUnauthorized());
        mvc.perform(multipart(path).file(new MockMultipartFile("files", "error.log", "text/plain", new byte[]{1})).with(anonymous()).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart(path).file(new MockMultipartFile("files", "error.log", "text/plain", new byte[]{1})))
                .andExpect(status().isForbidden());
        mvc.perform(put("/ticket/" + id).contentType("application/json").content(DETAILS)).andExpect(status().isForbidden());
        assertThat(attachments.count()).isZero();
    }

    private UUID createAutomatic() throws Exception {
        mvc.perform(post("/ticket").with(csrf()).contentType("application/json")
                .content("{\"autoTitle\":true,\"description\":\"La connexion est impossible en production\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("analysisStatus").value("PENDING"))
                .andExpect(jsonPath("severity").isEmpty()).andExpect(jsonPath("assignee").isEmpty());
        return tickets.findAll().getFirst().getId();
    }

    private TicketAnalyzedEvent analyzed(UUID id, String summary) {
        return new TicketAnalyzedEvent(id, TicketCategory.TECHNICAL, TicketPriority.LOW, summary, Instant.now(),
                tickets.findById(id).orElseThrow().getAnalysisRequestId(),
                com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketSeverity.BLOCKER,
                "Connexion indisponible", "dev@example.com",
                com.kova.aiticketinghub.ticketing.contracts.event.enums.AnalysisStatus.SUCCEEDED, null, "OLLAMA", "custom-model");
    }

    @Test
    void automaticAnalysisFillsFieldsAndDuplicateResultsAreIgnored() throws Exception {
        UUID id = createAutomatic();
        var result = analyzed(id, "Service de connexion bloqué");
        consumer.consume(result);
        consumer.consume(new TicketAnalyzedEvent(id, TicketCategory.OTHER, TicketPriority.HIGH, "Duplicate", Instant.now(),
                result.requestId(), result.severity(), "Duplicate", null, result.analysisStatus(), null, "OLLAMA", "custom-model"));
        mvc.perform(get("/ticket/" + id)).andExpect(jsonPath("title").value("Connexion indisponible"))
                .andExpect(jsonPath("severity").value("BLOCKER")).andExpect(jsonPath("assignee").value("dev@example.com"))
                .andExpect(jsonPath("category").value("TECHNICAL")).andExpect(jsonPath("priority").value("LOW"))
                .andExpect(jsonPath("summary").value("Service de connexion bloqué"))
                .andExpect(jsonPath("analysisStatus").value("SUCCEEDED")).andExpect(jsonPath("status").value("ANALYZED"));
        verify(appliedProducer, times(1)).send(any());
    }

    @Test
    void initialManualTitleSeverityAndAssignmentArePreserved() throws Exception {
        UUID id = create();
        consumer.consume(analyzed(id, "Analyse automatique"));
        mvc.perform(get("/ticket/" + id)).andExpect(jsonPath("title").value("Connexion impossible"))
                .andExpect(jsonPath("severity").value("CRITICAL")).andExpect(jsonPath("assignee").value("bob@example.com"));
    }

    @Test
    void retryUsesNewRequestAndDiscardsStaleAndUncorrelatedResults() throws Exception {
        UUID id = createAutomatic();
        var oldResult = analyzed(id, "Ancienne analyse");
        mvc.perform(post("/ticket/" + id + "/analysis").with(csrf())).andExpect(status().isOk());
        consumer.consume(oldResult);
        consumer.consume(new TicketAnalyzedEvent(id, TicketCategory.OTHER, TicketPriority.HIGH, "Sans identifiant", Instant.now()));
        mvc.perform(get("/ticket/" + id)).andExpect(jsonPath("analysisStatus").value("PENDING"))
                .andExpect(jsonPath("summary").isEmpty()).andExpect(jsonPath("severity").isEmpty());
        verifyNoInteractions(appliedProducer);
        consumer.consume(analyzed(id, "Nouvelle analyse"));
        mvc.perform(get("/ticket/" + id)).andExpect(jsonPath("summary").value("Nouvelle analyse"));
        verify(producer, times(2)).send(any());
    }

    @Test
    void failuresAreVisibleAndCanBeRetriedWithoutLosingFields() throws Exception {
        UUID id = create();
        var requestId = tickets.findById(id).orElseThrow().getAnalysisRequestId();
        consumer.consume(new TicketAnalyzedEvent(id, null, null, null, Instant.now(), requestId, null, null, null,
                com.kova.aiticketinghub.ticketing.contracts.event.enums.AnalysisStatus.FAILED,
                "PROVIDER_UNAVAILABLE", "OLLAMA", "model"));
        mvc.perform(get("/ticket/" + id)).andExpect(jsonPath("analysisStatus").value("FAILED"))
                .andExpect(jsonPath("analysisFailureCode").value("PROVIDER_UNAVAILABLE"))
                .andExpect(jsonPath("status").value("NEW")).andExpect(jsonPath("severity").value("CRITICAL"));
        verifyNoInteractions(appliedProducer);
        mvc.perform(post("/ticket/" + id + "/analysis").with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("analysisStatus").value("PENDING")).andExpect(jsonPath("analysisFailureCode").isEmpty());
    }

    @Test
    void contentEditsReanalyzeAndProtectTheEditedFields() throws Exception {
        UUID id = createAutomatic();
        var previous = analyzed(id, "Ancien contenu");
        mvc.perform(put("/ticket/" + id).with(csrf()).contentType("application/json")
                .content("{\"title\":\"Titre manuel\",\"description\":\"Nouveau contenu\",\"severity\":\"MINOR\",\"assignee\":\"manual@example.com\"}"))
                .andExpect(status().isOk());
        consumer.consume(previous);
        consumer.consume(analyzed(id, "Nouveau résumé"));
        mvc.perform(get("/ticket/" + id)).andExpect(jsonPath("title").value("Titre manuel"))
                .andExpect(jsonPath("severity").value("MINOR")).andExpect(jsonPath("assignee").value("manual@example.com"))
                .andExpect(jsonPath("summary").value("Nouveau résumé"));
        verify(producer, times(2)).send(any());
    }

    @Test
    void unchangedAutomaticFieldsRemainEligibleWhenSavingUnrelatedChanges() throws Exception {
        UUID id = createAutomatic();
        consumer.consume(analyzed(id, "Première analyse"));
        mvc.perform(put("/ticket/" + id).with(csrf()).contentType("application/json")
                .content("{\"title\":\"Connexion indisponible\",\"description\":\"La connexion est impossible en production\",\"severity\":\"BLOCKER\",\"assignee\":\"dev@example.com\",\"labels\":[\"production\"]}"))
                .andExpect(status().isOk());
        mvc.perform(post("/ticket/" + id + "/analysis").with(csrf())).andExpect(status().isOk());
        var result = analyzed(id, "Deuxième analyse");
        consumer.consume(new TicketAnalyzedEvent(id, result.category(), result.priority(), result.summary(), Instant.now(),
                result.requestId(), com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketSeverity.MAJOR,
                "Sujet actualisé", null, result.analysisStatus(), null, "OLLAMA", "model"));
        mvc.perform(get("/ticket/" + id)).andExpect(jsonPath("title").value("Sujet actualisé"))
                .andExpect(jsonPath("severity").value("MAJOR")).andExpect(jsonPath("assignee").isEmpty());
    }

    @Test
    void retryRequiresSessionAndCsrf() throws Exception {
        UUID id = create();
        mvc.perform(post("/ticket/" + id + "/analysis").with(anonymous()).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/ticket/" + id + "/analysis")).andExpect(status().isForbidden());
        verify(producer, times(1)).send(any());
    }

    @Test
    void legacyTicketsAndEventsKeepTheirExistingFields() throws Exception {
        var ticket = new com.kova.aiticketinghub.ticket.entity.Ticket();
        ticket.setTitle("Ancien titre");
        ticket.setDescription("Ancien contenu");
        ticket.setSeverity(com.kova.aiticketinghub.ticket.entity.TicketSeverity.MINOR);
        ticket.setAssignee("existing@example.com");
        ticket.setStatus(com.kova.aiticketinghub.ticket.entity.TicketStatus.NEW);
        ticket.setCreatedAt(Instant.now());
        ticket.setUpdatedAt(Instant.now());
        UUID id = tickets.save(ticket).getId();
        consumer.consume(new TicketAnalyzedEvent(id, TicketCategory.ACCOUNT, TicketPriority.MEDIUM, "Résumé historique", Instant.now()));
        mvc.perform(get("/ticket/" + id)).andExpect(jsonPath("title").value("Ancien titre"))
                .andExpect(jsonPath("severity").value("MINOR")).andExpect(jsonPath("assignee").value("existing@example.com"))
                .andExpect(jsonPath("category").value("ACCOUNT")).andExpect(jsonPath("analysisStatus").value("SUCCEEDED"));
    }
}

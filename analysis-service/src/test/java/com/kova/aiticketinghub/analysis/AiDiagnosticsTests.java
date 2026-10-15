package com.kova.aiticketinghub.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kova.aiticketinghub.analysis.config.AnalysisProperties;
import com.kova.aiticketinghub.analysis.DTO.TicketAnalysisResult;
import com.kova.aiticketinghub.analysis.llm.*;
import com.kova.aiticketinghub.analysis.service.TicketAnalysisService;
import com.kova.aiticketinghub.ticketing.contracts.ai.AiRuntimeConfiguration;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "analysis.internal-token=shared-test-internal-token-at-least-32-characters")
@AutoConfigureMockMvc
class AiDiagnosticsTests {
    private static final String AUTH = "Bearer shared-test-internal-token-at-least-32-characters";
    @Autowired MockMvc mvc;
    @MockitoBean HttpLlmClient client;
    @MockitoBean TicketAnalysisService service;
    private final ObjectMapper json = new ObjectMapper();
    private AiRuntimeConfiguration runtime(String model) {
        return new AiRuntimeConfiguration(true, "OLLAMA", model, "", "", "SCHEMA", 30000, 5000, 4096, 3, 0);
    }
    @BeforeEach void clear() { reset(client, service); }

    @Test void privateDiagnosticsRejectMissingAndInvalidInternalCredentials() throws Exception {
        for (String auth : List.of("", "Bearer wrong")) {
            mvc.perform(post("/internal/ai/test-connection").header("Authorization", auth)
                .contentType("application/json").content(json.writeValueAsString(runtime("model"))))
                .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(client, service);
    }

    @Test void ollamaDiscoveryWorksBeforeAnyModelIsSelected() throws Exception {
        when(client.listModels(any())).thenReturn(List.of("local-model:latest"));
        mvc.perform(post("/internal/ai/models").header("Authorization", AUTH)
            .contentType("application/json").content(json.writeValueAsString(runtime(""))))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value("local-model:latest"));
        verify(client).listModels(argThat(config -> config.getModel().isEmpty()));
    }

    @Test void connectionValidatesTheResponseAndUsesJustOneAttempt() throws Exception {
        when(client.complete(anyString(), anyString(), any(), any())).thenReturn("{\"ok\":true}");
        mvc.perform(post("/internal/ai/test-connection").header("Authorization", AUTH)
            .contentType("application/json").content(json.writeValueAsString(runtime("model"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        verify(client).complete(anyString(), contains("Aucune donnée réelle"), any(),
            argThat(config -> config.getMaxAttempts() == 1));
        when(client.complete(anyString(), anyString(), any(), any())).thenThrow(new LlmException("AUTHENTICATION_ERROR", false));
        mvc.perform(post("/internal/ai/test-connection").header("Authorization", AUTH)
            .contentType("application/json").content(json.writeValueAsString(runtime("model"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false));
    }

    @Test void syntheticAnalysisUsesTheNormalValidationWithoutPublishingATicket() throws Exception {
        when(service.analyze(any(), any())).thenReturn(new TicketAnalysisResult(TicketCategory.TECHNICAL,
            TicketPriority.HIGH, "Résumé validé", TicketSeverity.BLOCKER, "Sujet validé", null));
        mvc.perform(post("/internal/ai/test-analysis").header("Authorization", AUTH)
            .contentType("application/json").content(json.writeValueAsString(runtime("model"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.analysis.subject").value("Sujet validé"))
            .andExpect(jsonPath("$.analysis.assignee").isEmpty());
        verify(service).analyze(argThat(event -> event.title().contains("fictif")),
            argThat(config -> config.getMaxAttempts() == 1 && config.getModel().equals("model")));
    }

    @Test void missingModelIsRejectedWithoutCallingTheProvider() throws Exception {
        mvc.perform(post("/internal/ai/test-connection").header("Authorization", AUTH)
            .contentType("application/json").content(json.writeValueAsString(runtime(""))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(client, service);
    }
}
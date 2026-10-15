package com.kova.aiticketinghub.analysis.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kova.aiticketinghub.analysis.config.AnalysisProperties;
import com.kova.aiticketinghub.analysis.llm.*;
import com.kova.aiticketinghub.analysis.service.TicketAnalysisService;
import com.kova.aiticketinghub.ticketing.contracts.ai.*;
import com.kova.aiticketinghub.ticketing.contracts.event.TicketCreatedEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestController @RequestMapping("/internal/ai")
public class AiDiagnosticsController {
    private final HttpLlmClient client;
    private final TicketAnalysisService analysis;
    private final AnalysisProperties defaults;
    private final ObjectMapper json = new ObjectMapper();
    public AiDiagnosticsController(HttpLlmClient client, TicketAnalysisService analysis, AnalysisProperties defaults) {
        this.client = client; this.analysis = analysis; this.defaults = defaults;
    }
    private AnalysisProperties configuration(AiRuntimeConfiguration runtime, boolean requireModel) {
        try {
            var config = AnalysisProperties.fromRuntime(runtime, defaults, requireModel);
            config.setEnabled(true);
            config.setMaxAttempts(1);
            return config;
        } catch (IllegalArgumentException exception) { throw new LlmException("CONFIGURATION_ERROR", false); }
    }
    @PostMapping("/models")
    public List<String> models(@RequestBody AiRuntimeConfiguration runtime) {
        return client.listModels(configuration(runtime, false));
    }
    @PostMapping("/test-connection")
    public AiDiagnosticResult connection(@RequestBody AiRuntimeConfiguration runtime) {
        try {
            var schema = json.createObjectNode().put("type", "object").put("additionalProperties", false);
            schema.putObject("properties").putObject("ok").put("type", "boolean").putArray("enum").add(true);
            schema.putArray("required").add("ok");
            String response = client.complete("Réponds uniquement avec l’objet JSON {\"ok\":true}.",
                    "Test de connexion du logiciel de tickets. Aucune donnée réelle.", schema, configuration(runtime, true));
            var result = json.readTree(response);
            if (result == null || result.size() != 1 || !result.path("ok").isBoolean() || !result.path("ok").asBoolean())
                throw new LlmException("INVALID_RESPONSE", false);
            return new AiDiagnosticResult(true, "Connexion au modèle réussie.", null);
        } catch (LlmException exception) { return new AiDiagnosticResult(false, AiMessages.forCode(exception.code()), null); }
        catch (Exception exception) { return new AiDiagnosticResult(false, AiMessages.forCode("INVALID_RESPONSE"), null); }
    }
    @PostMapping("/test-analysis")
    public AiDiagnosticResult analyze(@RequestBody AiRuntimeConfiguration runtime) {
        try {
            var event = new TicketCreatedEvent(UUID.randomUUID(), "Exemple fictif : connexion indisponible",
                    "Tous les utilisateurs sont bloqués à la connexion en production, sans contournement. Erreur 500.",
                    Instant.now());
            var result = analysis.analyze(event, configuration(runtime, true));
            var fields = new LinkedHashMap<String,String>();
            fields.put("subject", result.subject()); fields.put("summary", result.summary());
            fields.put("category", result.category().name()); fields.put("priority", result.priority().name());
            fields.put("severity", result.severity().name()); fields.put("assignee", result.assignee());
            return new AiDiagnosticResult(true, "L’exemple fictif a été analysé et sa réponse validée.", fields);
        } catch (LlmException exception) { return new AiDiagnosticResult(false, AiMessages.forCode(exception.code()), null); }
    }
    @ExceptionHandler(LlmException.class)
    ResponseEntity<Map<String,String>> failure(LlmException exception) {
        return ResponseEntity.badRequest().body(Map.of("code", exception.code(), "message", AiMessages.forCode(exception.code())));
    }
    @ExceptionHandler({IllegalArgumentException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<Map<String,String>> invalid() {
        return ResponseEntity.badRequest().body(Map.of("code", "CONFIGURATION_ERROR", "message", AiMessages.forCode("CONFIGURATION_ERROR")));
    }
}

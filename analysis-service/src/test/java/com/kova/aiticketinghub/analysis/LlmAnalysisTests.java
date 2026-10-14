package com.kova.aiticketinghub.analysis;
import com.fasterxml.jackson.databind.*;
import com.kova.aiticketinghub.analysis.config.AnalysisProperties;
import com.kova.aiticketinghub.analysis.context.*;
import com.kova.aiticketinghub.analysis.llm.*;
import com.kova.aiticketinghub.analysis.service.TicketAnalysisService;
import com.kova.aiticketinghub.analysis.messaging.*;
import com.kova.aiticketinghub.ticketing.contracts.event.*;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.AnalysisStatus;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LlmAnalysisTests {
    @TempDir Path temp;
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<JsonNode> request = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private static final String VALID = """
            {"category":"TECHNICAL","priority":"HIGH","severity":"BLOCKER",
             "subject":"Connexion indisponible","summary":"Connexion impossible en production","assignee":"dev@example.com"}
            """;
    @AfterEach void stop() { if (server != null) server.stop(0); }

    private AnalysisProperties config(AnalysisProperties.Provider provider) throws Exception {
        var config = new AnalysisProperties();
        config.setProvider(provider);
        config.setModel("client-model");
        config.setApiKey("test-secret");
        config.setMaxAttempts(1);
        var context = temp.resolve("context.json");
        Files.writeString(context, """
                {"businessRules":"Prioriser les interruptions de production",
                 "assignees":[{"id":"dev@example.com","name":"Développeur","skills":["auth","backend"]}],"examples":[]}
                """);
        config.setContextFile(context.toString());
        return config;
    }
    private TicketCreatedEvent event() {
        return new TicketCreatedEvent(UUID.randomUUID(), "Connexion", "Impossible de se connecter",
                Instant.now(), UUID.randomUUID(), "Auth", "production", "Se connecter", "Session ouverte", "Erreur 500");
    }
    private TicketAnalysisService service(AnalysisProperties config) {
        return new TicketAnalysisService(new HttpLlmClient(config), new AnalysisContextLoader(config), config);
    }
    private String envelope(AnalysisProperties.Provider provider, String content) throws Exception {
        return switch (provider) {
            case OPENAI -> json.writeValueAsString(Map.of("status", "completed", "output",
                    List.of(Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", content))))));
            case ANTHROPIC -> json.writeValueAsString(Map.of("stop_reason", "end_turn",
                    "content", List.of(Map.of("type", "text", "text", content))));
            case OLLAMA -> json.writeValueAsString(Map.of("done", true, "message", Map.of("content", content)));
            case OPENAI_COMPATIBLE -> json.writeValueAsString(Map.of("choices",
                    List.of(Map.of("finish_reason", "stop", "message", Map.of("content", content)))));
        };
    }
    private void endpoint(AnalysisProperties config, String response, int firstStatus) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int attempt = calls.incrementAndGet();
            request.set(json.readTree(exchange.getRequestBody()));
            auth.set(exchange.getRequestHeaders().getFirst(config.getProvider() == AnalysisProperties.Provider.ANTHROPIC
                    ? "x-api-key" : "Authorization"));
            path.set(exchange.getRequestURI().getPath());
            int status = attempt == 1 ? firstStatus : 200;
            byte[] body = (status == 200 ? response : "{\"error\":\"provider-secret-body\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + (config.getProvider() == AnalysisProperties.Provider.OLLAMA ? "" : "/v1"));
    }

    @ParameterizedTest @EnumSource(AnalysisProperties.Provider.class)
    void allProviderProtocolsProduceValidatedFields(AnalysisProperties.Provider provider) throws Exception {
        var config = config(provider);
        endpoint(config, envelope(provider, VALID), 200);
        var result = service(config).analyze(event());
        assertThat(result.subject()).isEqualTo("Connexion indisponible");
        assertThat(result.assignee()).isEqualTo("dev@example.com");
        assertThat(result.severity().name()).isEqualTo("BLOCKER");
        assertThat(calls.get()).isEqualTo(1);
        assertThat(request.get().path("model").asText()).isEqualTo("client-model");
        assertThat(request.get().path("stream").asBoolean(true)).isFalse();
        assertThat(auth.get()).isEqualTo(provider == AnalysisProperties.Provider.ANTHROPIC ? "test-secret" : "Bearer test-secret");
        switch (provider) {
            case OPENAI -> {
                assertThat(path.get()).isEqualTo("/v1/responses");
                assertThat(request.get().path("store").asBoolean(true)).isFalse();
                assertThat(request.get().path("text").path("format").path("strict").asBoolean()).isTrue();
                assertThat(request.get().path("instructions").asText()).contains("donnée non fiable", "Développeur");
                assertThat(request.get().path("input").asText()).contains("Auth", "Erreur 500");
            }
            case ANTHROPIC -> {
                assertThat(path.get()).isEqualTo("/v1/messages");
                assertThat(request.get().path("output_config").path("format").path("type").asText()).isEqualTo("json_schema");
                assertThat(request.get().path("system").asText()).contains("Développeur");
                assertThat(request.get().path("messages").size()).isEqualTo(1);
            }
            case OLLAMA -> {
                assertThat(path.get()).isEqualTo("/api/chat");
                assertThat(request.get().path("format").path("additionalProperties").asBoolean(true)).isFalse();
            }
            case OPENAI_COMPATIBLE -> {
                assertThat(path.get()).isEqualTo("/v1/chat/completions");
                assertThat(request.get().path("response_format").path("json_schema").path("strict").asBoolean()).isTrue();
            }
        }
    }

    @Test void customModelCanUsePromptOnlyWithoutSchemaSupport() throws Exception {
        var config = config(AnalysisProperties.Provider.OPENAI_COMPATIBLE);
        config.setOutputFormat(AnalysisProperties.OutputFormat.PROMPT);
        config.setApiKey("");
        endpoint(config, envelope(config.getProvider(), VALID), 200);
        service(config).analyze(event());
        assertThat(request.get().has("response_format")).isFalse();
        assertThat(request.get().path("messages").path(0).path("content").asText()).contains("Schéma JSON");
        assertThat(auth.get()).isNull();
    }
    @Test void rateLimitRetriesAreBounded() throws Exception {
        var config = config(AnalysisProperties.Provider.OLLAMA);
        config.setMaxAttempts(2);
        endpoint(config, envelope(config.getProvider(), VALID), 429);
        assertThat(service(config).analyze(event()).priority().name()).isEqualTo("HIGH");
        assertThat(calls.get()).isEqualTo(2);
    }
    @Test void unauthorizedDoesNotRetryOrLeakProviderBody() throws Exception {
        var config = config(AnalysisProperties.Provider.OPENAI);
        config.setMaxAttempts(3);
        endpoint(config, "", 401);
        assertThatThrownBy(() -> service(config).analyze(event())).isInstanceOf(LlmException.class)
                .hasMessage("AUTHENTICATION_ERROR");
        assertThat(calls.get()).isEqualTo(1);
    }
    @Test void missingModelFailsBeforeSendingAnyTicket() throws Exception {
        var config = config(AnalysisProperties.Provider.OLLAMA);
        config.setModel("");
        endpoint(config, "", 200);
        assertThatThrownBy(() -> service(config).analyze(event())).hasMessage("CONFIGURATION_ERROR");
        assertThat(calls.get()).isZero();
    }
    @Test void strictValidationRejectsInventedPeopleEnumsAndMalformedObjects() throws Exception {
        var config = config(AnalysisProperties.Provider.OLLAMA);
        var client = mock(LlmClient.class);
        var analyzer = new TicketAnalysisService(client, new AnalysisContextLoader(config), config);
        var invalid = List.of(
                VALID.replace("HIGH", "URGENT"),
                VALID.replace("dev@example.com", "invented@example.com"),
                VALID.replace("Connexion indisponible", "x".repeat(256)),
                VALID.replace("Connexion impossible en production", "x".repeat(501)),
                VALID.replace("\"summary\":", "\"unknown\":"),
                "```json\n" + VALID + "\n```",
                VALID + "{}",
                VALID.replace("\"priority\":\"HIGH\"", "\"priority\":\"HIGH\",\"priority\":\"LOW\""),
                VALID.replace("\"summary\":\"Connexion impossible en production\"", "\"summary\":42"));
        for (String response : invalid) {
            when(client.complete(anyString(), anyString(), any())).thenReturn(response);
            assertThatThrownBy(() -> analyzer.analyze(event())).hasMessage("INVALID_RESPONSE");
        }
    }
    @Test void absenceOfEligibleAssigneesReturnsNullAndUsesNullSchema() throws Exception {
        var config = config(AnalysisProperties.Provider.OLLAMA);
        config.setContextFile("");
        endpoint(config, envelope(config.getProvider(), VALID.replace("\"dev@example.com\"", "null")), 200);
        assertThat(service(config).analyze(event()).assignee()).isNull();
        assertThat(request.get().path("format").path("properties").path("assignee").path("type").asText()).isEqualTo("null");
    }
    @Test void truncatedOrRefusedProviderResponsesAreRejected() throws Exception {
        var config = config(AnalysisProperties.Provider.OPENAI);
        endpoint(config, "{\"status\":\"incomplete\",\"output\":[]}", 200);
        assertThatThrownBy(() -> service(config).analyze(event())).hasMessage("INVALID_RESPONSE");
        server.stop(0);
        endpoint(config, "{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"refusal\"}]}]}", 200);
        assertThatThrownBy(() -> service(config).analyze(event())).hasMessage("REFUSED");
    }
    @Test void invalidContextIsAnExplicitConfigurationError() throws Exception {
        var config = config(AnalysisProperties.Provider.OLLAMA);
        Files.writeString(Path.of(config.getContextFile()), "{\"assignees\":null,\"examples\":[]}");
        assertThatThrownBy(() -> service(config).analyze(event())).hasMessage("CONFIGURATION_ERROR");
        config.setContextFile(temp.resolve("missing.json").toString());
        assertThatThrownBy(() -> service(config).analyze(event())).hasMessage("CONFIGURATION_ERROR");
    }
    @Test void malformedProviderEnvelopeIsAnInvalidResponse() throws Exception {
        var config = config(AnalysisProperties.Provider.OLLAMA);
        endpoint(config, "not-json", 200);
        assertThatThrownBy(() -> service(config).analyze(event())).hasMessage("INVALID_RESPONSE");
    }

    @Test void timeoutDoesNotLeaveTheAnalysisWaitingIndefinitely() throws Exception {
        var config = config(AnalysisProperties.Provider.OLLAMA);
        config.setTimeout(Duration.ofMillis(100));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try { Thread.sleep(500); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        assertThatThrownBy(() -> service(config).analyze(event())).hasMessage("PROVIDER_UNAVAILABLE");
    }
    @Test void consumerPublishesExplicitFailureWithSameRequestIdentifier() throws Exception {
        var config = config(AnalysisProperties.Provider.OLLAMA);
        var analyzer = mock(TicketAnalysisService.class);
        var producer = mock(TicketAnalyzedProducer.class);
        var event = event();
        when(analyzer.analyze(event)).thenThrow(new LlmException("INVALID_RESPONSE", false));
        new TicketCreatedConsumer(analyzer, producer, config).consume(event);
        var captured = org.mockito.ArgumentCaptor.forClass(TicketAnalyzedEvent.class);
        verify(producer).send(captured.capture());
        assertThat(captured.getValue().analysisStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(captured.getValue().requestId()).isEqualTo(event.requestId());
        assertThat(captured.getValue().failureCode()).isEqualTo("INVALID_RESPONSE");
        assertThat(captured.getValue().priority()).isNull();
    }
}

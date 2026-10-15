package com.kova.aiticketinghub.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kova.aiticketinghub.analysis.config.*;
import com.kova.aiticketinghub.analysis.context.AnalysisContextLoader;
import com.kova.aiticketinghub.analysis.llm.*;
import com.kova.aiticketinghub.analysis.messaging.*;
import com.kova.aiticketinghub.analysis.service.TicketAnalysisService;
import com.kova.aiticketinghub.ticketing.contracts.ai.AiRuntimeConfiguration;
import com.kova.aiticketinghub.ticketing.contracts.event.*;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.AnalysisStatus;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiRuntimeTests {
    private HttpServer server;
    private final ObjectMapper json = new ObjectMapper();
    private static final String TOKEN = "shared-test-internal-token-at-least-32-characters";
    private static final String VALID = """
        {"category":"TECHNICAL","priority":"HIGH","severity":"BLOCKER",
         "subject":"Connexion bloquée","summary":"Tous les utilisateurs sont bloqués","assignee":null}
        """;
    private AiRuntimeConfiguration runtime(String model, long version) {
        return new AiRuntimeConfiguration(true, "OLLAMA", model, "", "", "SCHEMA", 30000, 5000, 4096, 2, version);
    }
    private TicketCreatedEvent event() {
        return new TicketCreatedEvent(UUID.randomUUID(), "Connexion", "Erreur 500", Instant.now(),
            UUID.randomUUID(), null, null, null, null, null);
    }
    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void settingsAreReloadedForEveryAnalysisWithoutChangingAnExistingSnapshot() throws Exception {
        var current = new AtomicReference<>(runtime("first-model", 1));
        var auth = new AtomicReference<String>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = json.writeValueAsBytes(current.get());
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        var defaults = new AnalysisProperties();
        defaults.setInternalToken(TOKEN);
        defaults.setContextFile("server-context.json");
        defaults.setSettingsUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/internal/ai/configuration");
        var source = new HttpAnalysisSettingsSource(defaults);
        var first = source.snapshot();
        current.set(runtime("second-model", 2));
        var second = source.snapshot();
        assertThat(first.getModel()).isEqualTo("first-model");
        assertThat(second.getModel()).isEqualTo("second-model");
        assertThat(second.getSettingsVersion()).isEqualTo(2);
        assertThat(second.getContextFile()).isEqualTo("server-context.json");
        assertThat(auth.get()).isEqualTo("Bearer " + TOKEN);
    }

    @Test void configuredLinkNeverFallsBackToAnOldProviderOnFailureOrRedirect() throws Exception {
        var status = new AtomicInteger(401);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:1/steal");
            byte[] body = "private-provider-error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        var defaults = new AnalysisProperties();
        defaults.setModel("old-model"); defaults.setInternalToken(TOKEN);
        defaults.setSettingsUrl("http://127.0.0.1:" + server.getAddress().getPort());
        var source = new HttpAnalysisSettingsSource(defaults);
        assertThatThrownBy(source::snapshot).hasMessage("CONFIGURATION_ERROR");
        status.set(302);
        assertThatThrownBy(source::snapshot).hasMessage("PROVIDER_UNAVAILABLE");
    }

    @Test void legacyEnvironmentStillWorksWhenTheLinkHasNotBeenConfigured() {
        var defaults = new AnalysisProperties(); defaults.setModel("environment-model");
        var snapshot = new HttpAnalysisSettingsSource(defaults).snapshot();
        defaults.setModel("changed");
        assertThat(snapshot.getModel()).isEqualTo("environment-model");
        defaults.setInternalToken("too-short");
        assertThatThrownBy(() -> new HttpAnalysisSettingsSource(defaults).snapshot()).hasMessage("CONFIGURATION_ERROR");
    }

    @Test void ollamaModelDiscoveryUsesItsTagsEndpointWithoutRequiringAModel() throws Exception {
        var method = new AtomicReference<String>();
        var path = new AtomicReference<String>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod()); path.set(exchange.getRequestURI().getPath());
            byte[] body = "{\"models\":[{\"name\":\"tickets\"},{\"name\":\"local\"},{\"name\":\"tickets\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        var config = new AnalysisProperties();
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        assertThat(new HttpLlmClient().listModels(config)).containsExactly("local", "tickets");
        assertThat(method.get()).isEqualTo("GET");
        assertThat(path.get()).isEqualTo("/api/tags");
    }

    @Test void retriesAndMetadataUseTheSameConfigurationWhileTheAdministratorChangesIt() {
        var defaults = new AnalysisProperties(); defaults.setModel("first-model"); defaults.setMaxAttempts(2);
        var client = mock(LlmClient.class);
        var calls = new AtomicInteger();
        when(client.complete(anyString(), anyString(), any(), any())).thenAnswer(invocation -> {
            AnalysisProperties snapshot = invocation.getArgument(3);
            assertThat(snapshot.getModel()).isEqualTo("first-model");
            defaults.setModel("next-model");
            if (calls.incrementAndGet() == 1) throw new LlmException("PROVIDER_UNAVAILABLE", true);
            return VALID;
        });
        var source = mock(AnalysisSettingsSource.class);
        when(source.snapshot()).thenAnswer(invocation -> defaults.copy());
        var result = new TicketAnalysisService(client, new AnalysisContextLoader(defaults), source).analyze(event());
        assertThat(result.model()).isEqualTo("first-model");
        assertThat(result.provider()).isEqualTo("OLLAMA");
        assertThat(calls.get()).isEqualTo(2);
        verify(source, times(1)).snapshot();
    }

    @Test void disabledAnalysisDoesNotCallTheProviderAndPublishesAnExplicitFinalStatus() {
        var config = new AnalysisProperties(); config.setEnabled(false);
        var client = mock(LlmClient.class);
        var service = new TicketAnalysisService(client, new AnalysisContextLoader(config), config);
        var producer = mock(TicketAnalyzedProducer.class);
        var ticket = event();
        new TicketCreatedConsumer(service, producer).consume(ticket);
        verifyNoInteractions(client);
        verify(producer).send(argThat(result -> result.analysisStatus() == AnalysisStatus.DISABLED
            && result.requestId().equals(ticket.requestId()) && result.priority() == null));
    }
}

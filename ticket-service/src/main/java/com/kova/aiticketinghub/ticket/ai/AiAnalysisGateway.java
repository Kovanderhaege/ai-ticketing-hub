package com.kova.aiticketinghub.ticket.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.kova.aiticketinghub.ticketing.contracts.ai.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;

@Component
public class AiAnalysisGateway {
    private final AiConfigurationProperties properties;
    private final ObjectMapper json = new ObjectMapper();
    public AiAnalysisGateway(AiConfigurationProperties properties) { this.properties = properties; }

    private String post(String path, AiRuntimeConfiguration configuration) {
        try {
            var base = URI.create(properties.getAnalysisUrl());
            if (!List.of("http", "https").contains(base.getScheme()) || base.getHost() == null
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null)
                throw new IllegalArgumentException();
            var request = HttpRequest.newBuilder(URI.create(properties.getAnalysisUrl().replaceAll("/+$", "") + "/internal/ai/" + path))
                    .timeout(Duration.ofMillis(configuration.timeoutMillis() + 15000))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getInternalToken())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(configuration))).build();
            try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER).build()) {
                var result = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (result.statusCode() == 400 && result.body().length() < 8192) {
                    String code = json.readTree(result.body()).path("code").asText();
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, AiMessages.forCode(code));
                }
                if (result.statusCode() != 200 || result.body().length() > 1_048_576)
                    throw new IllegalStateException();
                return result.body();
            }
        } catch (ResponseStatusException exception) { throw exception; }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (Exception exception) { throw unavailable(); }
    }
    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Le service d’analyse est inaccessible ou sa liaison interne est mal configurée.");
    }
    public AiDiagnosticResult test(AiRuntimeConfiguration configuration, boolean analyze) {
        try { return json.readValue(post(analyze ? "test-analysis" : "test-connection", configuration), AiDiagnosticResult.class); }
        catch (ResponseStatusException exception) { throw exception; }
        catch (Exception exception) { throw unavailable(); }
    }
    public List<String> models(AiRuntimeConfiguration configuration) {
        try { return json.readValue(post("models", configuration), new TypeReference<List<String>>() {}); }
        catch (ResponseStatusException exception) { throw exception; }
        catch (Exception exception) { throw unavailable(); }
    }
}

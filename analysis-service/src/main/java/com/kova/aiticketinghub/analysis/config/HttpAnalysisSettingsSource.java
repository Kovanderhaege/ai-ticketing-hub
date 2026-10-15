package com.kova.aiticketinghub.analysis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kova.aiticketinghub.analysis.llm.LlmException;
import com.kova.aiticketinghub.ticketing.contracts.ai.*;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

@Component
public class HttpAnalysisSettingsSource implements AnalysisSettingsSource {
    private final AnalysisProperties defaults;
    private final ObjectMapper json = new ObjectMapper();
    public HttpAnalysisSettingsSource(AnalysisProperties defaults) { this.defaults = defaults; }
    @Override public AnalysisProperties snapshot() {
        if (defaults.getInternalToken().isBlank()) return defaults.copy();
        if (!InternalTokenVerifier.configured(defaults.getInternalToken())) throw new LlmException("CONFIGURATION_ERROR", false);
        try {
            var uri = URI.create(defaults.getSettingsUrl());
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
                throw new LlmException("CONFIGURATION_ERROR", false);
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + defaults.getInternalToken()).GET().build();
            try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                    .followRedirects(HttpClient.Redirect.NEVER).build()) {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 401 || response.statusCode() == 403)
                    throw new LlmException("CONFIGURATION_ERROR", false);
                if (response.statusCode() != 200) throw new LlmException("PROVIDER_UNAVAILABLE", false);
                if (response.body().length() > 32768) throw new LlmException("CONFIGURATION_ERROR", false);
                try {
                    return AnalysisProperties.fromRuntime(json.readValue(response.body(), AiRuntimeConfiguration.class), defaults);
                } catch (Exception exception) { throw new LlmException("CONFIGURATION_ERROR", false); }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new LlmException("PROVIDER_UNAVAILABLE", false);
        } catch (java.io.IOException exception) { throw new LlmException("PROVIDER_UNAVAILABLE", false); }
        catch (IllegalArgumentException exception) { throw new LlmException("CONFIGURATION_ERROR", false); }
    }
}

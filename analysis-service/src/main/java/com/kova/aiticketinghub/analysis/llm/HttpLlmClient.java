package com.kova.aiticketinghub.analysis.llm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kova.aiticketinghub.analysis.config.AnalysisProperties;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import static com.kova.aiticketinghub.analysis.config.AnalysisProperties.OutputFormat.*;
import static com.kova.aiticketinghub.analysis.config.AnalysisProperties.Provider.*;

@Component
public class HttpLlmClient implements LlmClient {
    private final AnalysisProperties config;
    private final ObjectMapper json = new ObjectMapper();
    public HttpLlmClient(AnalysisProperties config) { this.config = config; }

    @Override
    public String complete(String instructions, String ticket, JsonNode schema) {
        validateConfig();
        var body = json.createObjectNode().put("model", config.getModel()).put("stream", false);
        String path;
        if (config.getProvider() == OPENAI) {
            path = "/responses";
            body.put("instructions", instructions).put("input", ticket).put("store", false)
                    .put("max_output_tokens", config.getMaxTokens());
            if (config.getOutputFormat() == SCHEMA) {
                body.putObject("text").putObject("format").put("type", "json_schema")
                        .put("name", "ticket_analysis").put("strict", true).set("schema", schema);
            } else if (config.getOutputFormat() == JSON) {
                body.putObject("text").putObject("format").put("type", "json_object");
            }
        } else {
            var messages = body.putArray("messages");
            if (config.getProvider() == ANTHROPIC) body.put("system", instructions);
            else messages.addObject().put("role", "system").put("content", instructions);
            messages.addObject().put("role", "user").put("content", ticket);
            if (config.getProvider() == ANTHROPIC) {
                path = "/messages";
                body.put("max_tokens", config.getMaxTokens());
                if (config.getOutputFormat() == SCHEMA)
                    body.putObject("output_config").putObject("format").put("type", "json_schema").set("schema", schema);
            } else if (config.getProvider() == OLLAMA) {
                path = "/api/chat";
                body.putObject("options").put("temperature", 0).put("num_predict", config.getMaxTokens());
                if (config.getOutputFormat() == SCHEMA) body.set("format", schema);
                else if (config.getOutputFormat() == JSON) body.put("format", "json");
            } else {
                path = "/chat/completions";
                body.put("max_tokens", config.getMaxTokens());
                if (config.getOutputFormat() == SCHEMA) {
                    body.putObject("response_format").put("type", "json_schema").putObject("json_schema")
                            .put("name", "ticket_analysis").put("strict", true).set("schema", schema);
                } else if (config.getOutputFormat() == JSON)
                    body.putObject("response_format").put("type", "json_object");
            }
        }
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                    .timeout(config.getTimeout()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (config.getProvider() == ANTHROPIC)
                request.header("x-api-key", config.getApiKey()).header("anthropic-version", "2023-06-01");
            else if (!config.getApiKey().isBlank())
                request.header("Authorization", "Bearer " + config.getApiKey());
            try (var client = HttpClient.newBuilder().connectTimeout(config.getConnectTimeout())
                    .followRedirects(HttpClient.Redirect.NEVER).build()) {
                var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status < 200 || status >= 300)
                    throw new LlmException(status == 401 || status == 403 ? "AUTHENTICATION_ERROR" : "PROVIDER_ERROR",
                            status == 429 || status >= 500);
                if (response.body().length() > 1_048_576) throw new LlmException("INVALID_RESPONSE", false);
                try { return extract(json.readTree(response.body())); }
                catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                    throw new LlmException("INVALID_RESPONSE", true);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmException("PROVIDER_UNAVAILABLE", false);
        } catch (IOException exception) {
            throw new LlmException("PROVIDER_UNAVAILABLE", true);
        } catch (IllegalArgumentException exception) {
            throw new LlmException("CONFIGURATION_ERROR", false);
        }
    }

    private String extract(JsonNode response) {
        if (response == null || !response.isObject()) throw new LlmException("INVALID_RESPONSE", true);
        if (config.getProvider() == OPENAI) {
            if (!"completed".equals(response.path("status").asText())) throw new LlmException("INVALID_RESPONSE", true);
            var content = new StringBuilder();
            for (var output : response.path("output"))
                for (var block : output.path("content")) {
                    if ("refusal".equals(block.path("type").asText())) throw new LlmException("REFUSED", false);
                    if ("output_text".equals(block.path("type").asText())) content.append(block.path("text").asText());
                }
            return requireText(content.toString());
        }
        if (config.getProvider() == ANTHROPIC) {
            if ("refusal".equals(response.path("stop_reason").asText())) throw new LlmException("REFUSED", false);
            if (!"end_turn".equals(response.path("stop_reason").asText())) throw new LlmException("INVALID_RESPONSE", true);
            var content = new StringBuilder();
            for (var block : response.path("content"))
                if ("text".equals(block.path("type").asText())) content.append(block.path("text").asText());
            return requireText(content.toString());
        }
        if (config.getProvider() == OLLAMA) {
            if (!response.path("done").asBoolean() || "length".equals(response.path("done_reason").asText()))
                throw new LlmException("INVALID_RESPONSE", true);
            return requireText(response.path("message").path("content").asText());
        }
        var choice = response.path("choices").path(0);
        var refusal = choice.path("message").path("refusal");
        if (!refusal.isMissingNode() && !refusal.isNull() && !refusal.asText().isBlank())
            throw new LlmException("REFUSED", false);
        if (!choice.path("finish_reason").isMissingNode() && !"stop".equals(choice.path("finish_reason").asText()))
            throw new LlmException("INVALID_RESPONSE", true);
        return requireText(choice.path("message").path("content").asText());
    }

    private String requireText(String value) {
        if (value.isBlank()) throw new LlmException("INVALID_RESPONSE", true);
        return value;
    }
    private String baseUrl() {
        if (!config.getBaseUrl().isBlank()) return config.getBaseUrl().replaceAll("/+$", "");
        return switch (config.getProvider()) {
            case OPENAI -> "https://api.openai.com/v1";
            case ANTHROPIC -> "https://api.anthropic.com/v1";
            case OLLAMA -> "http://localhost:11434";
            case OPENAI_COMPATIBLE -> throw new LlmException("CONFIGURATION_ERROR", false);
        };
    }
    private void validateConfig() {
        if (config.getModel().isBlank() || config.getTimeout().isNegative() || config.getTimeout().isZero()
                || config.getTimeout().toSeconds() > 120 || config.getConnectTimeout().isNegative()
                || config.getConnectTimeout().isZero() || config.getMaxTokens() < 1 || config.getMaxTokens() > 32768
                || ((config.getProvider() == OPENAI || config.getProvider() == ANTHROPIC) && config.getApiKey().isBlank())
                || (config.getProvider() == ANTHROPIC && config.getOutputFormat() == JSON))
            throw new LlmException("CONFIGURATION_ERROR", false);
        try {
            var uri = URI.create(baseUrl());
            if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
                throw new LlmException("CONFIGURATION_ERROR", false);
        } catch (IllegalArgumentException exception) { throw new LlmException("CONFIGURATION_ERROR", false); }
    }
}

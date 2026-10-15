package com.kova.aiticketinghub.ticketing.contracts.ai;

import java.net.URI;
import java.util.Set;

public record AiRuntimeConfiguration(boolean enabled, String provider, String model, String baseUrl,
        String apiKey, String outputFormat, long timeoutMillis, long connectTimeoutMillis,
        int maxTokens, int maxAttempts, long version) {
    public AiRuntimeConfiguration validate(boolean requireModel) {
        if (provider == null || !Set.of("OPENAI", "ANTHROPIC", "OLLAMA", "OPENAI_COMPATIBLE").contains(provider)
                || outputFormat == null || !Set.of("SCHEMA", "JSON", "PROMPT").contains(outputFormat)
                || model == null || model.length() > 255 || (requireModel && model.isBlank())
                || baseUrl == null || baseUrl.length() > 2048 || apiKey == null || apiKey.length() > 4096
                || apiKey.contains("\r") || apiKey.contains("\n")
                || timeoutMillis < 100 || timeoutMillis > 120000 || connectTimeoutMillis < 100 || connectTimeoutMillis > 30000
                || maxTokens < 1 || maxTokens > 32768 || maxAttempts < 1 || maxAttempts > 3
                || ("ANTHROPIC".equals(provider) && "JSON".equals(outputFormat)))
            throw new IllegalArgumentException("Invalid AI configuration");
        var uri = URI.create(effectiveBaseUrl());
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Invalid AI server URL");
        if (requireModel && Set.of("OPENAI", "ANTHROPIC").contains(provider) && apiKey.isBlank())
            throw new IllegalArgumentException("Missing provider credential");
        return this;
    }

    public String effectiveBaseUrl() {
        if (baseUrl != null && !baseUrl.isBlank()) return baseUrl.replaceAll("/+$", "");
        return switch (provider) {
            case "OPENAI" -> "https://api.openai.com/v1";
            case "ANTHROPIC" -> "https://api.anthropic.com/v1";
            case "OLLAMA" -> "http://localhost:11434";
            default -> throw new IllegalArgumentException("Missing AI server URL");
        };
    }

    @Override public String toString() {
        return "AiRuntimeConfiguration[provider=" + provider + ", version=" + version + ", credentials=REDACTED]";
    }
}

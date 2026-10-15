package com.kova.aiticketinghub.ticket.ai;

import jakarta.validation.constraints.*;

public record AiSettingsDraft(boolean enabled, @NotBlank @Size(max = 32) String provider,
        @NotNull @Size(max = 255) String model, @NotNull @Size(max = 2048) String baseUrl,
        @Size(max = 4096) String apiKey, boolean clearApiKey, @NotBlank @Size(max = 16) String outputFormat,
        @Min(100) @Max(120000) long timeoutMillis, @Min(100) @Max(30000) long connectTimeoutMillis,
        @Min(1) @Max(32768) int maxTokens, @Min(1) @Max(3) int maxAttempts, @Min(0) long version) {
    @Override public String toString() { return "AiSettingsDraft[credentials=REDACTED]"; }
}

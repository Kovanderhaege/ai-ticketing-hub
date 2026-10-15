package com.kova.aiticketinghub.ticket.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import com.kova.aiticketinghub.ticketing.contracts.ai.AiRuntimeConfiguration;

@Getter @Setter @Component
@ConfigurationProperties(prefix = "app.ai")
public class AiConfigurationProperties {
    private String internalToken = "";
    private String analysisUrl = "http://localhost:8081";
    private String encryptionKey = "";
    private String encryptionKeyFile = "./data/ai-settings.key";
    private boolean enabled = true;
    private String provider = "OLLAMA";
    private String model = "";
    private String baseUrl = "";
    private String apiKey = "";
    private String outputFormat = "SCHEMA";
    private java.time.Duration timeout = java.time.Duration.ofSeconds(30);
    private java.time.Duration connectTimeout = java.time.Duration.ofSeconds(5);
    private int maxTokens = 4096;
    private int maxAttempts = 2;
    public AiRuntimeConfiguration defaults() {
        return new AiRuntimeConfiguration(enabled, provider, model, baseUrl, apiKey, outputFormat,
                timeout.toMillis(), connectTimeout.toMillis(), maxTokens, maxAttempts, 0);
    }
}

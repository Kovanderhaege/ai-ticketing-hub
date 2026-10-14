package com.kova.aiticketinghub.analysis.config;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.Duration;

@Getter @Setter
@Component
@ConfigurationProperties(prefix = "analysis")
public class AnalysisProperties {
    public enum Provider { OPENAI, ANTHROPIC, OLLAMA, OPENAI_COMPATIBLE }
    public enum OutputFormat { SCHEMA, JSON, PROMPT }
    private Provider provider = Provider.OLLAMA;
    private OutputFormat outputFormat = OutputFormat.SCHEMA;
    private String model = "";
    private String baseUrl = "";
    private String apiKey = "";
    private String contextFile = "";
    private Duration timeout = Duration.ofSeconds(30);
    private Duration connectTimeout = Duration.ofSeconds(5);
    private int maxTokens = 4096;
    private int maxAttempts = 2;
}
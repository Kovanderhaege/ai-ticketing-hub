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
    private boolean enabled = true;
    private String settingsUrl = "http://localhost:8083/internal/ai/configuration";
    private String internalToken = "";
    private long settingsVersion;
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
    public AnalysisProperties copy() {
        var result = new AnalysisProperties();
        result.enabled = enabled; result.settingsUrl = settingsUrl; result.internalToken = internalToken;
        result.settingsVersion = settingsVersion; result.provider = provider; result.outputFormat = outputFormat;
        result.model = model; result.baseUrl = baseUrl; result.apiKey = apiKey; result.contextFile = contextFile;
        result.timeout = timeout; result.connectTimeout = connectTimeout; result.maxTokens = maxTokens;
        result.maxAttempts = maxAttempts;
        return result;
    }
    public static AnalysisProperties fromRuntime(
            com.kova.aiticketinghub.ticketing.contracts.ai.AiRuntimeConfiguration runtime, AnalysisProperties defaults) {
        return fromRuntime(runtime, defaults, runtime.enabled());
    }
    public static AnalysisProperties fromRuntime(
            com.kova.aiticketinghub.ticketing.contracts.ai.AiRuntimeConfiguration runtime,
            AnalysisProperties defaults, boolean requireModel) {
        runtime.validate(requireModel);
        var result = defaults.copy();
        result.enabled = runtime.enabled(); result.provider = Provider.valueOf(runtime.provider());
        result.model = runtime.model(); result.baseUrl = runtime.baseUrl(); result.apiKey = runtime.apiKey();
        result.outputFormat = OutputFormat.valueOf(runtime.outputFormat());
        result.timeout = Duration.ofMillis(runtime.timeoutMillis());
        result.connectTimeout = Duration.ofMillis(runtime.connectTimeoutMillis());
        result.maxTokens = runtime.maxTokens(); result.maxAttempts = runtime.maxAttempts();
        result.settingsVersion = runtime.version();
        return result;
    }
}

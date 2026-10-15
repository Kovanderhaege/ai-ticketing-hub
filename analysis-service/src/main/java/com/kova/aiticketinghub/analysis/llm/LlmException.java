package com.kova.aiticketinghub.analysis.llm;

public class LlmException extends RuntimeException {
    private final String code;
    private final boolean retryable;
    public LlmException(String code, boolean retryable) {
        // Never expose a provider response body or credential in errors.
        super(code);
        this.code = code;
        this.retryable = retryable;
    }
    private String provider;
    private String model;
    public LlmException withConfiguration(com.kova.aiticketinghub.analysis.config.AnalysisProperties config) {
        provider = config.getProvider().name(); model = config.getModel(); return this;
    }
    public String provider() { return provider; }
    public String model() { return model; }
    public String code() { return code; }
    public boolean retryable() { return retryable; }
}
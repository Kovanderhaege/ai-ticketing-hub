package com.kova.aiticketinghub.ticketing.contracts.ai;
import java.util.Map;
public record AiDiagnosticResult(boolean success, String message, Map<String, String> analysis) {}

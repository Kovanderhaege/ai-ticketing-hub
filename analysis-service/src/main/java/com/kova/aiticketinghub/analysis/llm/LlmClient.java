package com.kova.aiticketinghub.analysis.llm;
import com.fasterxml.jackson.databind.JsonNode;
public interface LlmClient {
    String complete(String instructions, String ticket, JsonNode schema);
}
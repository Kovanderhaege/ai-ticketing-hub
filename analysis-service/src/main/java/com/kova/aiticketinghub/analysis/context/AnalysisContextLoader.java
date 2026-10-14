package com.kova.aiticketinghub.analysis.context;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kova.aiticketinghub.analysis.config.AnalysisProperties;
import com.kova.aiticketinghub.analysis.llm.LlmException;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashSet;

@Component
public class AnalysisContextLoader {
    private final AnalysisProperties config;
    private final ObjectMapper json = new ObjectMapper();
    public AnalysisContextLoader(AnalysisProperties config) { this.config = config; }
    public AnalysisContext load() {
        if (config.getContextFile().isBlank()) return AnalysisContext.empty();
        try {
            var path = Path.of(config.getContextFile());
            if (Files.size(path) > 262_144) throw new LlmException("CONFIGURATION_ERROR", false);
            var context = json.readValue(Files.readString(path), AnalysisContext.class);
            if (context == null || context.assignees() == null || context.examples() == null
                    || context.assignees().size() > 200 || context.examples().size() > 20)
                throw new LlmException("CONFIGURATION_ERROR", false);
            var ids = new HashSet<String>();
            for (var assignee : context.assignees())
                if (assignee == null || assignee.id() == null || assignee.id().isBlank() || assignee.id().length() > 255
                        || !assignee.id().equals(assignee.id().strip()) || !ids.add(assignee.id()) || assignee.skills() == null)
                    throw new LlmException("CONFIGURATION_ERROR", false);
            return context;
        } catch (IOException | IllegalArgumentException exception) { throw new LlmException("CONFIGURATION_ERROR", false); }
    }
}
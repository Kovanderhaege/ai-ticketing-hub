package com.kova.aiticketinghub.analysis.context;
import java.util.List;
public record AnalysisContext(String businessRules, List<Assignee> assignees, List<Example> examples) {
    public record Assignee(String id, String name, List<String> skills) {}
    public record Example(String description, String category, String priority, String severity, String subject, String assignee) {}
    public static AnalysisContext empty() { return new AnalysisContext("", List.of(), List.of()); }
}
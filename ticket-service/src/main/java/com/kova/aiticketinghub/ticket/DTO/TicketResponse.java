package com.kova.aiticketinghub.ticket.DTO;


import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        String title,
        String description,
        String status,
        String category,
        String priority,
        String summary,
        Instant createdAt,
        Instant updatedAt,
        String reporter,
        String assignee,
        String type,
        String severity,
        String component,
        String affectedVersion,
        String fixVersion,
        LocalDate dueDate,
        String environment,
        String stepsToReproduce,
        String expectedResult,
        String actualResult,
        String rootCause,
        String technicalSolution,
        List<String> labels,
        List<AttachmentResponse> attachments,
        String analysisStatus,
        String analysisFailureCode,
        String analysisProvider,
        String analysisModel
) {}

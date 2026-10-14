package com.kova.aiticketinghub.ticket.DTO;

import java.time.Instant;
import java.util.UUID;

public record AttachmentResponse(UUID id, String filename, String contentType, long size,
                                 Instant createdAt, String uploadedBy) {}

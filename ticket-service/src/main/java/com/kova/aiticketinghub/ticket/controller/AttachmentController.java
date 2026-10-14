package com.kova.aiticketinghub.ticket.controller;

import com.kova.aiticketinghub.ticket.DTO.AttachmentResponse;
import com.kova.aiticketinghub.ticket.service.AttachmentService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/ticket/{ticketId}/attachments")
public class AttachmentController {
    private final AttachmentService service;
    public AttachmentController(AttachmentService service) { this.service = service; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<AttachmentResponse> upload(@PathVariable UUID ticketId, @RequestPart("files") List<MultipartFile> files,
                                          Authentication authentication) {
        return service.upload(ticketId, files, authentication.getName());
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<byte[]> download(@PathVariable UUID ticketId, @PathVariable UUID attachmentId) {
        var file = service.download(ticketId, attachmentId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.getSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.getFilename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(file.getData());
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID ticketId, @PathVariable UUID attachmentId) { service.delete(ticketId, attachmentId); }
}

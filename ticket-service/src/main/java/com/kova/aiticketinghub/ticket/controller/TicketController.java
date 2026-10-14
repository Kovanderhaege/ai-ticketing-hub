package com.kova.aiticketinghub.ticket.controller;

import com.kova.aiticketinghub.ticket.DTO.CreateTicketRequest;
import com.kova.aiticketinghub.ticket.DTO.TicketResponse;
import com.kova.aiticketinghub.ticket.service.TicketService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/ticket")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public TicketResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping
    public List<TicketResponse> getAll() {
        return service.getAll();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public TicketResponse create(@Valid @RequestBody CreateTicketRequest request, Authentication authentication) {
        return service.create(request, authentication.getName(), List.of());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TicketResponse createWithFiles(@Valid @RequestPart("ticket") CreateTicketRequest request,
                                         @RequestPart(value = "files", required = false) List<MultipartFile> files,
                                         Authentication authentication) {
        return service.create(request, authentication.getName(), files);
    }

    @PutMapping("/{id}")
    public TicketResponse update(@PathVariable UUID id, @Valid @RequestBody CreateTicketRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/analysis")
    public TicketResponse reanalyze(@PathVariable UUID id) {
        return service.reanalyze(id);
    }
}

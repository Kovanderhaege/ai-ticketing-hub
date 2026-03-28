package com.kova.aiticketinghub.controller;

import com.kova.aiticketinghub.DTO.CreateTicketRequest;
import com.kova.aiticketinghub.DTO.TicketResponse;
import com.kova.aiticketinghub.service.TicketService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
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

    @PostMapping
    public TicketResponse create(@RequestBody CreateTicketRequest request) {
        return service.create(request);
    }
}
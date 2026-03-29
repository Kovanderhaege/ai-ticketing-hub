package com.kova.aiticketinghub.ticket.controller;

import com.kova.aiticketinghub.ticket.DTO.CreateTicketRequest;
import com.kova.aiticketinghub.ticket.DTO.TicketResponse;
import com.kova.aiticketinghub.ticket.service.TicketService;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    public TicketResponse create(@RequestBody CreateTicketRequest request) {
        return service.create(request);
    }
}
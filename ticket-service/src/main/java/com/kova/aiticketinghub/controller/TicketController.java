package com.kova.aiticketinghub.controller;

import com.kova.aiticketinghub.DTO.CreateTicketRequest;
import com.kova.aiticketinghub.DTO.TicketResponse;
import com.kova.aiticketinghub.service.TicketService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @PostMapping
    public TicketResponse create(@RequestBody CreateTicketRequest request) {
        return service.create(request);
    }
}
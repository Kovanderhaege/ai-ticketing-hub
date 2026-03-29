package com.kova.aiticketinghub.notification.controller;

import com.kova.aiticketinghub.notification.DTO.NotificationResult;
import com.kova.aiticketinghub.notification.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResult> getAll() {
        return notificationService.getAll();
    }
}

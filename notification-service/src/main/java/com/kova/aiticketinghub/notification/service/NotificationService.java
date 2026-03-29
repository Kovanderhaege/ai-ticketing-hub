package com.kova.aiticketinghub.notification.service;

import com.kova.aiticketinghub.notification.DTO.NotificationResult;
import com.kova.aiticketinghub.ticketing.contracts.event.TicketAnalyzedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class.getName());

    private final List<NotificationResult> notificationResults = new CopyOnWriteArrayList<>();

    public void handleHighPriority(TicketAnalyzedEvent event) {
        notificationResults.add(toNotification(event));
        log.debug("ALERT HIGH PRIORITY: {}", event.ticketId());
    }

    private NotificationResult toNotification(TicketAnalyzedEvent event) {
        return new NotificationResult(
                event.ticketId(),
                "High priority ticket detected",
                event.priority(),
                Instant.now()
        );
    }

    public List<NotificationResult> getAll() {
        return notificationResults;
    }
}
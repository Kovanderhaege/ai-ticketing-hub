package com.kova.aiticketinghub.notification.service;

import com.kova.aiticketinghub.notification.entity.Notification;
import com.kova.aiticketinghub.notification.repository.NotificationRepository;
import com.kova.aiticketinghub.ticketing.contracts.event.TicketAnalyzedEvent;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class.getName());

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void handleHighPriority(TicketAnalyzedEvent event) {
        notificationRepository.save(toNotification(event));
        log.debug("ALERT HIGH PRIORITY: {}", event.ticketId());
    }

    private Notification toNotification(TicketAnalyzedEvent event) {
        Notification notification = new Notification();
        notification.setTicketId(event.ticketId());
        notification.setMessage("High priority ticket detected");
        notification.setPriority(TicketPriority.HIGH);
        notification.setCreatedAt(Instant.now());

        return notification;
    }

    public List<Notification> getAll() {
        return notificationRepository.findAll();
    }
}
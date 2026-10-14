package com.kova.aiticketinghub.notification.messaging;

import com.kova.aiticketinghub.notification.service.NotificationService;
import com.kova.aiticketinghub.ticketing.contracts.event.TicketAnalyzedEvent;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.TicketPriority;
import com.kova.aiticketinghub.ticketing.contracts.event.enums.AnalysisStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TicketAnalyzedConsumer {

    private final NotificationService notificationService;

    public TicketAnalyzedConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "ticket.analysis-applied.v1", groupId = "notification-service")
    public void consume(TicketAnalyzedEvent event) {
        if(event.analysisStatus() != AnalysisStatus.FAILED && event.priority() == TicketPriority.HIGH){
            notificationService.handleHighPriority(event);
        }
    }
}

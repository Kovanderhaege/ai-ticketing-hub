package com.kova.aiticketinghub.analysis.messaging;

import com.kova.aiticketinghub.analysis.DTO.TicketAnalysisResult;
import com.kova.aiticketinghub.analysis.service.TicketAnalysisService;
import com.kova.aiticketinghub.ticketing.contracts.event.TicketAnalyzedEvent;
import com.kova.aiticketinghub.ticketing.contracts.event.TicketCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TicketCreatedConsumer {

    private TicketAnalysisService ticketAnalysisService;

    private TicketAnalyzedProducer producer;

    public TicketCreatedConsumer(TicketAnalysisService ticketAnalysisService, TicketAnalyzedProducer ticketAnalyzedProducer) {
        this.ticketAnalysisService = ticketAnalysisService;
        this.producer = ticketAnalyzedProducer;
    }

    @KafkaListener(topics = "ticket.created.v1", groupId = "analysis-service")
    public void consume(TicketCreatedEvent event) {
        TicketAnalysisResult result =  ticketAnalysisService.analyze(event);

        TicketAnalyzedEvent analyzedEvent = new TicketAnalyzedEvent(
                event.ticketId(),
                result.category(),
                result.priority(),
                result.summary(),
                Instant.now()
        );

        producer.send(analyzedEvent);

        System.out.println("Published ticket.analyzed.v1: " + analyzedEvent);
    }
}

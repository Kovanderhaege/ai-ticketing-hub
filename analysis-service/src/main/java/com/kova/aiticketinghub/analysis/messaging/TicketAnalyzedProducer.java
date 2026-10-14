package com.kova.aiticketinghub.analysis.messaging;
import com.kova.aiticketinghub.ticketing.contracts.event.TicketAnalyzedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
public class TicketAnalyzedProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    public TicketAnalyzedProducer(KafkaTemplate<String, Object> kafkaTemplate) { this.kafkaTemplate = kafkaTemplate; }
    public void send(TicketAnalyzedEvent event) {
        try {
            kafkaTemplate.send("ticket.analyzed.v1", event.ticketId().toString(), event).get(15, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Analysis event publication interrupted");
        } catch (Exception exception) {
            throw new IllegalStateException("Analysis event publication failed");
        }
    }
}
package com.kova.aiticketinghub.ticket;

import com.kova.aiticketinghub.ticket.entity.Ticket;
import com.kova.aiticketinghub.ticket.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ticket-schema-upgrade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error=true",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:legacy-ticket-schema.sql"
})
class TicketSchemaUpgradeTests {
    @Autowired TicketRepository tickets;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test @org.springframework.transaction.annotation.Transactional
    void upgradingAPopulatedTablePreservesOldTicketsAndInitializesTracking() {
        var legacy = tickets.findById(UUID.fromString("00000000-0000-0000-0000-000000000001")).orElseThrow();
        assertThat(legacy.getTitle()).isEqualTo("Ticket existant avant la mise à jour");
        assertThat(legacy.isAnalysisFieldsTracked()).isFalse();
        assertThat(legacy.getManualAnalysisFields()).isEmpty();

        var created = new Ticket();
        created.setTitle("Nouveau ticket");
        created.setAnalysisFieldsTracked(true);
        UUID id = tickets.saveAndFlush(created).getId();
        entityManager.clear();
        assertThat(tickets.findById(id).orElseThrow().isAnalysisFieldsTracked()).isTrue();
    }
}

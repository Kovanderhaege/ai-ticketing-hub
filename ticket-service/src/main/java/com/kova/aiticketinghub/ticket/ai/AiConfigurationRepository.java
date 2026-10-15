package com.kova.aiticketinghub.ticket.ai;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface AiConfigurationRepository extends JpaRepository<AiConfigurationEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select configuration from AiConfigurationEntity configuration where configuration.id = 1")
    Optional<AiConfigurationEntity> findLocked();
}

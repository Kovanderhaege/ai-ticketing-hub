package com.kova.aiticketinghub.ticket.ai;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Getter @Setter @Table(name = "ai_configuration")
public class AiConfigurationEntity {
    @Id private Long id = 1L;
    @Version private Long rowVersion;
    private long revision;
    private boolean enabled;
    @Column(length = 32) private String provider;
    @Column(length = 255) private String model;
    @Column(length = 2048) private String baseUrl;
    @Column(length = 8192) private String encryptedApiKey;
    @Column(length = 16) private String outputFormat;
    private long timeoutMillis;
    private long connectTimeoutMillis;
    private int maxTokens;
    private int maxAttempts;
    private Instant updatedAt;
    @Column(length = 254) private String updatedBy;
}

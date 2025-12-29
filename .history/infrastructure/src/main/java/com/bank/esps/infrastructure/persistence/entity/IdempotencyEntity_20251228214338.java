package com.bank.esps.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * JPA entity for idempotency tracking
 */
@Entity
@Table(name = "idempotency", indexes = {
    @Index(name = "idx_idempotency_message_id", columnList = "message_id"),
    @Index(name = "idx_idempotency_position_key", columnList = "position_key"),
    @Index(name = "idx_idempotency_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "message_id", nullable = false, unique = true, length = 255)
    private String messageId;
    
    @Column(name = "position_key", nullable = false, length = 255)
    private String positionKey;
    
    @Column(name = "status", nullable = false, length = 20)
    private String status = "PROCESSED";
    
    @Column(name = "event_version")
    private Integer eventVersion;
    
    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;
}

package com.bank.esps.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "basket_activity")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BasketActivityEntity {

    @Id
    @Column(name = "activity_id", length = 64)
    private String activityId;

    @Column(name = "contract_id", nullable = false, length = 64)
    private String contractId;

    @Column(name = "security_id", length = 255)
    private String securityId;

    @Column(name = "position_key", nullable = false, length = 255)
    private String positionKey;

    @Column(name = "upi", length = 255)
    private String upi;

    @Column(name = "account", length = 255)
    private String account;

    @Column(name = "book", length = 255)
    private String book;

    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    @Column(name = "product_type", nullable = false, length = 32)
    private String productType;

    @Column(name = "product_qualifier", length = 64)
    private String productQualifier;

    @Column(name = "underlier_type", length = 32)
    private String underlierType;

    @Column(name = "underlier_id", length = 255)
    private String underlierId;

    @Column(name = "currency", length = 16)
    private String currency;

    @Column(name = "tax_lot_method", nullable = false, length = 16)
    private String taxLotMethod;

    @Column(name = "position_status", nullable = false, length = 16)
    private String positionStatus;

    @Column(name = "realized_pnl", nullable = false, precision = 28, scale = 8)
    private BigDecimal realizedPnl;

    @Column(name = "product_json", columnDefinition = "NVARCHAR(MAX)")
    @JdbcTypeCode(SqlTypes.JSON)
    private String productJson;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}

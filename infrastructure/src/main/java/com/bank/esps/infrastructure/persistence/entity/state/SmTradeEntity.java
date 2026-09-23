package com.bank.esps.infrastructure.persistence.entity.state;

import com.bank.esps.domain.state.ActivityType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "sm_trade")
@Getter
@Setter
public class SmTradeEntity {
    @Id
    @Column(name = "trade_id", length = 128)
    private String tradeId;

    @Column(name = "position_key", nullable = false, length = 64)
    private String positionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 16)
    private ActivityType activityType;

    @Column(name = "quantity", precision = 28, scale = 8)
    private BigDecimal quantity;

    @Column(name = "price", nullable = false, precision = 28, scale = 8)
    private BigDecimal price;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "region", nullable = false, length = 32)
    private String region;

    @Column(name = "applied_at", nullable = false)
    private OffsetDateTime appliedAt;

    @Column(name = "account", length = 64)
    private String account;

    @Column(name = "instrument", length = 64)
    private String instrument;

    @Column(name = "currency", length = 8)
    private String currency;
}

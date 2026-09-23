package com.bank.esps.infrastructure.persistence.entity.state;

import com.bank.esps.domain.state.LifecycleStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "sm_position_history")
@Getter
@Setter
public class SmPositionHistoryEntity {
    @Id
    @Column(name = "history_id", length = 36)
    private String historyId;

    @Column(name = "position_key", nullable = false, length = 64)
    private String positionKey;

    @Column(name = "trade_id", nullable = false, length = 128)
    private String tradeId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "qty_before", nullable = false, precision = 28, scale = 8)
    private BigDecimal qtyBefore;

    @Column(name = "qty_after", nullable = false, precision = 28, scale = 8)
    private BigDecimal qtyAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_before", length = 16)
    private LifecycleStatus statusBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_after", nullable = false, length = 16)
    private LifecycleStatus statusAfter;

    @Column(name = "open_price_after", nullable = false, precision = 28, scale = 8)
    private BigDecimal openPriceAfter;
}

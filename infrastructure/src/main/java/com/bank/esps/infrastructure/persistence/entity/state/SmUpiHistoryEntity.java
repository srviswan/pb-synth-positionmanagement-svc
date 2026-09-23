package com.bank.esps.infrastructure.persistence.entity.state;

import com.bank.esps.domain.state.UpiChangeType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "sm_upi_history")
@Getter
@Setter
public class SmUpiHistoryEntity {
    @Id
    @Column(name = "history_id", length = 36)
    private String historyId;

    @Column(name = "position_key", nullable = false, length = 64)
    private String positionKey;

    @Column(name = "upi", nullable = false, length = 128)
    private String upi;

    @Column(name = "previous_upi", length = 128)
    private String previousUpi;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 32)
    private UpiChangeType changeType;

    @Column(name = "trade_id", nullable = false, length = 128)
    private String tradeId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;
}

package com.bank.esps.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contract rules for tax lot allocation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contract {
    private String contractId;
    private String taxLotMethod; // FIFO, LIFO, HIFO
    private String account;
    private String instrument;
    private String currency;
}

package com.bank.esps.domain.cdm.product;

import com.bank.esps.domain.cdm.base.Account;
import com.bank.esps.domain.cdm.base.Book;
import com.bank.esps.domain.cdm.base.Party;
import com.bank.esps.domain.enums.TaxLotMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Legal contract that hedge trades are allocated to. Aligns with CDM
 * {@code ContractDetails} / {@code Trade.contractDetails}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialContract {
    private String contractId;
    private FinancialProduct product;
    private Party party1;
    private Party party2;
    private Account account;
    private Book book;
    private LocalDate startDate;
    private LocalDate endDate;
    @Builder.Default
    private TaxLotMethod taxLotMethod = TaxLotMethod.FIFO;
}

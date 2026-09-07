package com.bank.esps.domain.cdm.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Party account used for position aggregation and access control.
 * Aligns with CDM {@code Account}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    private String accountId;
    private String accountName;
    private String accountBeneficiary;

    public static Account of(String accountId) {
        return Account.builder().accountId(accountId).build();
    }
}

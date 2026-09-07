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

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "financial_contract")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialContractEntity {

    @Id
    @Column(name = "contract_id", length = 64)
    private String contractId;

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

    @Column(name = "account", length = 255)
    private String account;

    @Column(name = "book", length = 255)
    private String book;

    @Column(name = "party1", length = 255)
    private String party1;

    @Column(name = "party2", length = 255)
    private String party2;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "tax_lot_method", nullable = false, length = 16)
    private String taxLotMethod;

    @Column(name = "product_json", columnDefinition = "NVARCHAR(MAX)")
    @JdbcTypeCode(SqlTypes.JSON)
    private String productJson;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}

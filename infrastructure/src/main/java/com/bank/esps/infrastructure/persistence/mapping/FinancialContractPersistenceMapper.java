package com.bank.esps.infrastructure.persistence.mapping;

import com.bank.esps.domain.cdm.base.Account;
import com.bank.esps.domain.cdm.base.Book;
import com.bank.esps.domain.cdm.base.Party;
import com.bank.esps.domain.cdm.product.FinancialContract;
import com.bank.esps.domain.cdm.product.FinancialProduct;
import com.bank.esps.domain.cdm.product.ProductType;
import com.bank.esps.domain.enums.TaxLotMethod;
import com.bank.esps.infrastructure.persistence.entity.FinancialContractEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class FinancialContractPersistenceMapper {

    private final ObjectMapper objectMapper;

    public FinancialContractPersistenceMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FinancialContractEntity toEntity(FinancialContract contract) {
        FinancialProduct product = contract.getProduct();
        return FinancialContractEntity.builder()
                .contractId(contract.getContractId())
                .productType(product != null && product.getProductType() != null
                        ? product.getProductType().name() : ProductType.SWAP.name())
                .productQualifier(product != null ? product.getProductQualifier() : null)
                .underlierType(product != null && product.underlierType() != null ? product.underlierType().name() : null)
                .underlierId(product != null ? product.underlierId() : null)
                .currency(product != null ? product.getCurrency() : null)
                .account(contract.getAccount() != null ? contract.getAccount().getAccountId() : null)
                .book(contract.getBook() != null ? contract.getBook().getBookId() : null)
                .party1(contract.getParty1() != null ? contract.getParty1().getPartyId() : null)
                .party2(contract.getParty2() != null ? contract.getParty2().getPartyId() : null)
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .taxLotMethod(contract.getTaxLotMethod() != null ? contract.getTaxLotMethod().name() : TaxLotMethod.FIFO.name())
                .productJson(writeJson(product))
                .updatedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .build();
    }

    public FinancialContract toDomain(FinancialContractEntity entity) {
        FinancialProduct product = readProduct(entity.getProductJson());
        return FinancialContract.builder()
                .contractId(entity.getContractId())
                .product(product)
                .party1(entity.getParty1() == null ? null : Party.of(entity.getParty1()))
                .party2(entity.getParty2() == null ? null : Party.of(entity.getParty2()))
                .account(entity.getAccount() == null ? null : Account.of(entity.getAccount()))
                .book(entity.getBook() == null ? null : Book.of(entity.getBook()))
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .taxLotMethod(entity.getTaxLotMethod() == null
                        ? TaxLotMethod.FIFO
                        : TaxLotMethod.valueOf(entity.getTaxLotMethod()))
                .build();
    }

    private String writeJson(FinancialProduct product) {
        if (product == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(product);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize financial product", e);
        }
    }

    private FinancialProduct readProduct(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, FinancialProduct.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to deserialize financial product", e);
        }
    }
}

package com.bank.esps.api.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class PositionV1Dtos {
    private PositionV1Dtos() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private String error;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BasketComponentDto {
        private String identifier;
        private String type;
        private BigDecimal weight;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnderlierDto {
        private String type;
        private String identifier;
        private String identifierScheme;
        private String instrumentClass;
        private String description;
        private String currency;
        private String exchange;
        @Builder.Default
        private List<BasketComponentDto> constituents = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductDto {
        private String productType;
        private String productQualifier;
        private String currency;
        private UnderlierDto underlier;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContractRequest {
        private String contractId;
        private String productType;
        private String productQualifier;
        private String currency;
        private String accountId;
        private String bookId;
        private String party1Id;
        private String party2Id;
        private LocalDate startDate;
        private LocalDate endDate;
        private String taxLotMethod;
        private UnderlierDto underlier;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContractResponse {
        private String contractId;
        private String productType;
        private String productQualifier;
        private String currency;
        private String accountId;
        private String bookId;
        private String party1Id;
        private String party2Id;
        private LocalDate startDate;
        private LocalDate endDate;
        private String taxLotMethod;
        private UnderlierDto underlier;
        private ProductDto product;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionRequest {
        private String tradeId;
        private String securityId;
        private BigDecimal quantity;
        private BigDecimal price;
        private String currency;
        private LocalDate tradeDate;
        private LocalDate settlementDate;
        private LocalDate effectiveDate;
        private String accountId;
        private String bookId;
        private String taxLotMethod;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionDto {
        private String detailId;
        private String tradeId;
        private String securityId;
        private BigDecimal quantity;
        private BigDecimal price;
        private String currency;
        private LocalDate tradeDate;
        private LocalDate effectiveDate;
        private LocalDate settlementDate;
        private String allocationStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenLotDto {
        private String lotId;
        private String sourceDetailId;
        private String sourceTradeId;
        private BigDecimal originalQuantity;
        private BigDecimal remainingQuantity;
        private BigDecimal costBasis;
        private LocalDate tradeDate;
        private LocalDate settlementDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClosingLotDto {
        private String closingLotId;
        private String openedLotId;
        private String closingDetailId;
        private String closingTradeId;
        private BigDecimal closedQuantity;
        private BigDecimal closePrice;
        private BigDecimal costBasis;
        private BigDecimal realizedPnL;
        private LocalDate tradeDate;
        private LocalDate settlementDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementDto {
        private String settlementId;
        private String detailId;
        private String tradeId;
        private LocalDate settlementDate;
        private BigDecimal settledQuantity;
        private String currency;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementRequest {
        private String detailId;
        private String tradeId;
        private LocalDate settlementDate;
        private BigDecimal settledQuantity;
        private String currency;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DividendRequest {
        private String dividendId;
        private LocalDate exDate;
        private LocalDate payDate;
        private BigDecimal rate;
        private BigDecimal amount;
        private String currency;
        private String action;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DividendOpenLotDto {
        private String lotId;
        private String sourceOpenLotId;
        private String dividendId;
        private LocalDate exDate;
        private LocalDate payDate;
        private BigDecimal quantity;
        private BigDecimal remainingQuantity;
        private BigDecimal rate;
        private BigDecimal amount;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DividendClosingLotDto {
        private String closingLotId;
        private String openedDividendLotId;
        private String dividendId;
        private BigDecimal closedQuantity;
        private BigDecimal amount;
        private LocalDate payDate;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PositionResponse {
        private String positionId;
        private String contractId;
        private String securityId;
        private String direction;
        private String positionKey;
        private String accountId;
        private String bookId;
        private String upi;
        private String status;
        private Integer version;
        private BigDecimal quantity;
        private BigDecimal realizedPnL;
        private String currency;
        private ProductDto product;
        private List<TransactionDto> transactions;
        private List<OpenLotDto> openLots;
        private List<ClosingLotDto> closingLots;
        private List<SettlementDto> settlements;
        private List<DividendOpenLotDto> dividendOpenLots;
        private List<DividendClosingLotDto> dividendClosingLots;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionListResponse {
        private String contractId;
        @Builder.Default
        private List<PositionResponse> positions = new ArrayList<>();
    }
}

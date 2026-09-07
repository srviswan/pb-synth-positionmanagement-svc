package com.bank.esps.api.v1.mapper;

import com.bank.esps.api.v1.dto.PositionV1Dtos.BasketComponentDto;
import com.bank.esps.api.v1.dto.PositionV1Dtos.ClosingLotDto;
import com.bank.esps.api.v1.dto.PositionV1Dtos.ContractRequest;
import com.bank.esps.api.v1.dto.PositionV1Dtos.ContractResponse;
import com.bank.esps.api.v1.dto.PositionV1Dtos.DividendClosingLotDto;
import com.bank.esps.api.v1.dto.PositionV1Dtos.DividendOpenLotDto;
import com.bank.esps.api.v1.dto.PositionV1Dtos.DividendRequest;
import com.bank.esps.api.v1.dto.PositionV1Dtos.OpenLotDto;
import com.bank.esps.api.v1.dto.PositionV1Dtos.PositionListResponse;
import com.bank.esps.api.v1.dto.PositionV1Dtos.PositionResponse;
import com.bank.esps.api.v1.dto.PositionV1Dtos.ProductDto;
import com.bank.esps.api.v1.dto.PositionV1Dtos.SettlementDto;
import com.bank.esps.api.v1.dto.PositionV1Dtos.SettlementRequest;
import com.bank.esps.api.v1.dto.PositionV1Dtos.TransactionDto;
import com.bank.esps.api.v1.dto.PositionV1Dtos.TransactionRequest;
import com.bank.esps.api.v1.dto.PositionV1Dtos.UnderlierDto;
import com.bank.esps.application.cdm.PositionV1Service;
import com.bank.esps.application.cdm.PositionV1Service.ContractCommand;
import com.bank.esps.application.cdm.PositionV1Service.TransactionCommand;
import com.bank.esps.domain.cdm.basket.BasketActivity;
import com.bank.esps.domain.cdm.basket.BasketActivityDetail;
import com.bank.esps.domain.cdm.basket.BasketSettlement;
import com.bank.esps.domain.cdm.basket.ClosingLot;
import com.bank.esps.domain.cdm.basket.DividendClosingLot;
import com.bank.esps.domain.cdm.basket.DividendOpenLot;
import com.bank.esps.domain.cdm.basket.OpenLot;
import com.bank.esps.domain.cdm.event.DividendInstruction;
import com.bank.esps.domain.cdm.event.SettlementInstruction;
import com.bank.esps.domain.cdm.product.BasketComponent;
import com.bank.esps.domain.cdm.product.FinancialContract;
import com.bank.esps.domain.cdm.product.FinancialProduct;
import com.bank.esps.domain.cdm.product.ProductType;
import com.bank.esps.domain.cdm.product.Underlier;
import com.bank.esps.domain.cdm.product.UnderlierType;
import com.bank.esps.domain.enums.TaxLotMethod;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PositionV1Mapper {

    public ContractCommand toCommand(ContractRequest request) {
        if (request.getContractId() == null || request.getContractId().isBlank()) {
            throw new IllegalArgumentException("contractId is required");
        }
        if (request.getUnderlier() == null || request.getUnderlier().getIdentifier() == null) {
            throw new IllegalArgumentException("underlier.identifier is required");
        }
        return new ContractCommand(
                request.getContractId(),
                parseProductType(request.getProductType()),
                request.getProductQualifier(),
                request.getCurrency(),
                request.getAccountId(),
                request.getBookId(),
                request.getParty1Id(),
                request.getParty2Id(),
                request.getStartDate(),
                request.getEndDate(),
                parseTaxLot(request.getTaxLotMethod()),
                toUnderlier(request.getUnderlier()));
    }

    public TransactionCommand toCommand(TransactionRequest request) {
        if (request.getTradeId() == null || request.getTradeId().isBlank()) {
            throw new IllegalArgumentException("tradeId is required");
        }
        if (request.getSecurityId() == null || request.getSecurityId().isBlank()) {
            throw new IllegalArgumentException("securityId is required");
        }
        if (request.getQuantity() == null) {
            throw new IllegalArgumentException("quantity is required");
        }
        if (request.getPrice() == null) {
            throw new IllegalArgumentException("price is required");
        }
        if (request.getTradeDate() == null) {
            throw new IllegalArgumentException("tradeDate is required");
        }
        return new TransactionCommand(
                request.getTradeId(),
                request.getSecurityId(),
                request.getQuantity(),
                request.getPrice(),
                request.getCurrency(),
                request.getTradeDate(),
                request.getSettlementDate(),
                request.getEffectiveDate(),
                request.getAccountId(),
                request.getBookId(),
                parseTaxLot(request.getTaxLotMethod()));
    }

    public DividendInstruction toInstruction(DividendRequest request) {
        if (request.getDividendId() == null || request.getDividendId().isBlank()) {
            throw new IllegalArgumentException("dividendId is required");
        }
        DividendInstruction.Action action = DividendInstruction.Action.OPEN;
        if (request.getAction() != null && !request.getAction().isBlank()) {
            action = DividendInstruction.Action.valueOf(request.getAction().trim().toUpperCase(Locale.ROOT));
        }
        return DividendInstruction.builder()
                .dividendId(request.getDividendId())
                .exDate(request.getExDate())
                .payDate(request.getPayDate())
                .rate(request.getRate())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .action(action)
                .build();
    }

    public SettlementInstruction toInstruction(SettlementRequest request) {
        return SettlementInstruction.builder()
                .detailId(request.getDetailId())
                .tradeId(request.getTradeId())
                .settlementDate(request.getSettlementDate())
                .settledQuantity(request.getSettledQuantity())
                .currency(request.getCurrency())
                .status(request.getStatus() != null ? request.getStatus() : "SETTLED")
                .build();
    }

    public ContractResponse toContract(FinancialContract contract) {
        FinancialProduct product = contract.getProduct();
        UnderlierDto underlier = product != null ? toUnderlierDto(product.getUnderlier()) : null;
        return ContractResponse.builder()
                .contractId(contract.getContractId())
                .productType(product != null && product.getProductType() != null ? product.getProductType().name() : null)
                .productQualifier(product != null ? product.getProductQualifier() : null)
                .currency(product != null ? product.getCurrency() : null)
                .accountId(contract.getAccount() != null ? contract.getAccount().getAccountId() : null)
                .bookId(contract.getBook() != null ? contract.getBook().getBookId() : null)
                .party1Id(contract.getParty1() != null ? contract.getParty1().getPartyId() : null)
                .party2Id(contract.getParty2() != null ? contract.getParty2().getPartyId() : null)
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .taxLotMethod(contract.getTaxLotMethod() != null ? contract.getTaxLotMethod().name() : null)
                .underlier(underlier)
                .product(toProduct(product))
                .build();
    }

    public PositionListResponse toPositionList(String contractId, List<BasketActivity> activities, String include) {
        boolean embed = include != null && !include.isBlank();
        return PositionListResponse.builder()
                .contractId(contractId)
                .positions(activities.stream().map(activity -> toPosition(activity, embed ? include : "")).toList())
                .build();
    }

    public PositionResponse toPosition(BasketActivity activity, String include) {
        Set<String> parts = parseInclude(include);
        boolean all = parts.isEmpty() && include == null;
        boolean none = include != null && include.isBlank();
        ProductDto product = toProduct(activity.getProduct());
        PositionResponse.PositionResponseBuilder builder = PositionResponse.builder()
                .positionId(activity.getActivityId())
                .contractId(activity.getContractId())
                .securityId(activity.resolvedSecurityId())
                .direction(activity.getDirection() != null ? activity.getDirection().name() : null)
                .positionKey(activity.getPositionKey())
                .accountId(activity.getAccount() != null ? activity.getAccount().getAccountId() : null)
                .bookId(activity.getBook() != null ? activity.getBook().getBookId() : null)
                .upi(activity.getUpi())
                .status(activity.status() != null ? activity.status().name() : null)
                .version(activity.getVersion())
                .quantity(activity.totalQuantity())
                .realizedPnL(activity.getRealizedPnL())
                .currency(activity.getProduct() != null ? activity.getProduct().getCurrency() : null)
                .product(product);
        if (!none && (all || parts.contains("transactions"))) {
            builder.transactions(activity.getDetails().stream().map(this::toTransaction).toList());
        }
        if (!none && (all || parts.contains("lots"))) {
            builder.openLots(activity.getOpenLots().stream().map(this::toOpenLot).toList());
            builder.closingLots(activity.getClosingLots().stream().map(this::toClosingLot).toList());
        }
        if (!none && (all || parts.contains("settlements"))) {
            builder.settlements(activity.getSettlements().stream().map(this::toSettlement).toList());
        }
        if (!none && (all || parts.contains("dividends"))) {
            builder.dividendOpenLots(safe(activity.getDividendOpenLots()).stream().map(this::toDividendOpen).toList());
            builder.dividendClosingLots(safe(activity.getDividendClosingLots()).stream().map(this::toDividendClose).toList());
        }
        return builder.build();
    }

    public TransactionDto toTransaction(BasketActivityDetail detail) {
        return TransactionDto.builder()
                .detailId(detail.getDetailId())
                .tradeId(detail.getTradeId())
                .securityId(detail.getUnderlier() != null ? detail.getUnderlier().getIdentifier() : null)
                .quantity(detail.getQuantity())
                .price(detail.getPrice())
                .currency(detail.getCurrency())
                .tradeDate(detail.getTradeDate())
                .effectiveDate(detail.getEffectiveDate())
                .settlementDate(detail.getSettlementDate())
                .allocationStatus(detail.getAllocationStatus())
                .build();
    }

    public OpenLotDto toOpenLot(OpenLot lot) {
        return OpenLotDto.builder()
                .lotId(lot.getLotId())
                .sourceDetailId(lot.getSourceDetailId())
                .sourceTradeId(lot.getSourceTradeId())
                .originalQuantity(lot.getOriginalQuantity())
                .remainingQuantity(lot.getRemainingQuantity())
                .costBasis(lot.getCostBasis())
                .tradeDate(lot.getTradeDate())
                .settlementDate(lot.getSettlementDate())
                .build();
    }

    public ClosingLotDto toClosingLot(ClosingLot lot) {
        return ClosingLotDto.builder()
                .closingLotId(lot.getClosingLotId())
                .openedLotId(lot.getOpenedLotId())
                .closingDetailId(lot.getClosingDetailId())
                .closingTradeId(lot.getClosingTradeId())
                .closedQuantity(lot.getClosedQuantity())
                .closePrice(lot.getClosePrice())
                .costBasis(lot.getCostBasis())
                .realizedPnL(lot.getRealizedPnL())
                .tradeDate(lot.getTradeDate())
                .settlementDate(lot.getSettlementDate())
                .build();
    }

    public SettlementDto toSettlement(BasketSettlement settlement) {
        return SettlementDto.builder()
                .settlementId(settlement.getSettlementId())
                .detailId(settlement.getDetailId())
                .tradeId(settlement.getTradeId())
                .settlementDate(settlement.getSettlementDate())
                .settledQuantity(settlement.getSettledQuantity())
                .currency(settlement.getCurrency())
                .status(settlement.getStatus())
                .build();
    }

    public DividendOpenLotDto toDividendOpen(DividendOpenLot lot) {
        return DividendOpenLotDto.builder()
                .lotId(lot.getLotId())
                .sourceOpenLotId(lot.getSourceOpenLotId())
                .dividendId(lot.getDividendId())
                .exDate(lot.getExDate())
                .payDate(lot.getPayDate())
                .quantity(lot.getQuantity())
                .remainingQuantity(lot.getRemainingQuantity())
                .rate(lot.getRate())
                .amount(lot.getAmount())
                .currency(lot.getCurrency())
                .build();
    }

    public DividendClosingLotDto toDividendClose(DividendClosingLot lot) {
        return DividendClosingLotDto.builder()
                .closingLotId(lot.getClosingLotId())
                .openedDividendLotId(lot.getOpenedDividendLotId())
                .dividendId(lot.getDividendId())
                .closedQuantity(lot.getClosedQuantity())
                .amount(lot.getAmount())
                .payDate(lot.getPayDate())
                .currency(lot.getCurrency())
                .build();
    }

    private ProductDto toProduct(FinancialProduct product) {
        if (product == null) {
            return null;
        }
        return ProductDto.builder()
                .productType(product.getProductType() != null ? product.getProductType().name() : null)
                .productQualifier(product.getProductQualifier())
                .currency(product.getCurrency())
                .underlier(toUnderlierDto(product.getUnderlier()))
                .build();
    }

    private Underlier toUnderlier(UnderlierDto dto) {
        List<BasketComponent> constituents = dto.getConstituents() == null ? List.of() : dto.getConstituents().stream()
                .map(component -> BasketComponent.builder()
                        .underlier(Underlier.builder()
                                .type(parseUnderlierType(component.getType()))
                                .identifier(component.getIdentifier())
                                .currency(component.getCurrency())
                                .build())
                        .weight(component.getWeight())
                        .build())
                .toList();
        return PositionV1Service.underlierFrom(
                parseUnderlierType(dto.getType()),
                dto.getIdentifier(),
                dto.getIdentifierScheme(),
                dto.getInstrumentClass(),
                dto.getDescription(),
                dto.getCurrency(),
                dto.getExchange(),
                constituents);
    }

    private UnderlierDto toUnderlierDto(Underlier underlier) {
        if (underlier == null) {
            return null;
        }
        List<BasketComponentDto> constituents = underlier.getConstituents() == null ? List.of()
                : underlier.getConstituents().stream()
                .map(component -> BasketComponentDto.builder()
                        .identifier(component.getUnderlier() != null ? component.getUnderlier().getIdentifier() : null)
                        .type(component.getUnderlier() != null && component.getUnderlier().getType() != null
                                ? component.getUnderlier().getType().name() : null)
                        .weight(component.getWeight())
                        .currency(component.getUnderlier() != null ? component.getUnderlier().getCurrency() : null)
                        .build())
                .toList();
        return UnderlierDto.builder()
                .type(underlier.getType() != null ? underlier.getType().name() : null)
                .identifier(underlier.getIdentifier())
                .identifierScheme(underlier.getIdentifierScheme())
                .instrumentClass(underlier.getInstrumentClass())
                .description(underlier.getDescription())
                .currency(underlier.getCurrency())
                .exchange(underlier.getExchange())
                .constituents(constituents)
                .build();
    }

    private static Set<String> parseInclude(String include) {
        if (include == null || include.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(include.split(","))
                .map(part -> part.trim().toLowerCase(Locale.ROOT))
                .filter(part -> !part.isBlank())
                .collect(Collectors.toSet());
    }

    private static ProductType parseProductType(String value) {
        if (value == null || value.isBlank()) {
            return ProductType.SWAP;
        }
        return ProductType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private static UnderlierType parseUnderlierType(String value) {
        if (value == null || value.isBlank()) {
            return UnderlierType.EQUITY;
        }
        return UnderlierType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private static TaxLotMethod parseTaxLot(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return TaxLotMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    private static <T> List<T> safe(List<T> list) {
        return list == null ? new ArrayList<>() : list;
    }
}

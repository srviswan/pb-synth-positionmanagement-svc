package com.bank.esps.infrastructure.persistence.mapping;

import com.bank.esps.domain.cdm.base.Account;
import com.bank.esps.domain.cdm.base.Book;
import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.basket.BasketActivity;
import com.bank.esps.domain.cdm.basket.BasketActivityDetail;
import com.bank.esps.domain.cdm.basket.BasketSettlement;
import com.bank.esps.domain.cdm.basket.ClosingLot;
import com.bank.esps.domain.cdm.basket.OpenLot;
import com.bank.esps.domain.cdm.position.ClosedState;
import com.bank.esps.domain.cdm.position.ClosedStateReason;
import com.bank.esps.domain.cdm.position.LifecycleState;
import com.bank.esps.domain.cdm.position.PositionStatus;
import com.bank.esps.domain.cdm.product.FinancialProduct;
import com.bank.esps.domain.cdm.product.ProductType;
import com.bank.esps.domain.cdm.product.Underlier;
import com.bank.esps.domain.enums.TaxLotMethod;
import com.bank.esps.infrastructure.persistence.entity.BasketActivityDetailEntity;
import com.bank.esps.infrastructure.persistence.entity.BasketActivityEntity;
import com.bank.esps.infrastructure.persistence.entity.BasketClosingLotEntity;
import com.bank.esps.infrastructure.persistence.entity.BasketOpenLotEntity;
import com.bank.esps.infrastructure.persistence.entity.BasketSettlementEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class BasketActivityPersistenceMapper {

    private final ObjectMapper objectMapper;

    public BasketActivityPersistenceMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BasketActivityEntity toEntity(BasketActivity activity) {
        FinancialProduct product = activity.getProduct();
        return BasketActivityEntity.builder()
                .activityId(activity.getActivityId())
                .contractId(activity.getContractId())
                .positionKey(activity.getPositionKey())
                .upi(activity.getUpi())
                .account(activity.getAccount() != null ? activity.getAccount().getAccountId() : null)
                .book(activity.getBook() != null ? activity.getBook().getBookId() : null)
                .direction(activity.getDirection() != null ? activity.getDirection().name() : PositionDirection.LONG.name())
                .productType(product != null && product.getProductType() != null ? product.getProductType().name() : ProductType.SWAP.name())
                .productQualifier(product != null ? product.getProductQualifier() : null)
                .underlierType(product != null && product.underlierType() != null ? product.underlierType().name() : null)
                .underlierId(product != null ? product.underlierId() : null)
                .currency(product != null ? product.getCurrency() : null)
                .taxLotMethod(activity.getTaxLotMethod() != null ? activity.getTaxLotMethod().name() : TaxLotMethod.FIFO.name())
                .positionStatus(activity.status().name())
                .realizedPnl(activity.getRealizedPnL())
                .productJson(writeJson(product))
                .version(activity.getVersion())
                .updatedAt(activity.getUpdatedAt() != null ? activity.getUpdatedAt() : OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .build();
    }

    public BasketActivity toDomain(BasketActivityEntity entity,
                                   List<BasketActivityDetailEntity> details,
                                   List<BasketOpenLotEntity> openLots,
                                   List<BasketClosingLotEntity> closingLots,
                                   List<BasketSettlementEntity> settlements) {
        FinancialProduct product = readProduct(entity.getProductJson());
        PositionStatus status = PositionStatus.valueOf(entity.getPositionStatus());
        LifecycleState lifecycle = status == PositionStatus.CLOSED
                ? LifecycleState.closed(ClosedState.builder().reason(ClosedStateReason.TERMINATED).build())
                : LifecycleState.builder().positionStatus(status).build();
        return BasketActivity.builder()
                .activityId(entity.getActivityId())
                .contractId(entity.getContractId())
                .product(product)
                .account(entity.getAccount() == null ? null : Account.of(entity.getAccount()))
                .book(entity.getBook() == null ? null : Book.of(entity.getBook()))
                .direction(PositionDirection.valueOf(entity.getDirection()))
                .positionKey(entity.getPositionKey())
                .upi(entity.getUpi())
                .taxLotMethod(TaxLotMethod.valueOf(entity.getTaxLotMethod()))
                .lifecycle(lifecycle)
                .version(entity.getVersion())
                .realizedPnL(entity.getRealizedPnl())
                .updatedAt(entity.getUpdatedAt())
                .details(details.stream().map(this::toDetail).collect(java.util.stream.Collectors.toCollection(ArrayList::new)))
                .openLots(openLots.stream().map(this::toOpenLot).collect(java.util.stream.Collectors.toCollection(ArrayList::new)))
                .closingLots(closingLots.stream().map(this::toClosingLot).collect(java.util.stream.Collectors.toCollection(ArrayList::new)))
                .settlements(settlements.stream().map(this::toSettlement).collect(java.util.stream.Collectors.toCollection(ArrayList::new)))
                .build();
    }

    public BasketActivityDetailEntity toDetailEntity(String activityId, BasketActivityDetail detail) {
        return BasketActivityDetailEntity.builder()
                .detailId(detail.getDetailId())
                .activityId(activityId)
                .tradeId(detail.getTradeId())
                .underlierId(detail.getUnderlier() != null ? detail.getUnderlier().getIdentifier() : null)
                .quantity(detail.getQuantity())
                .price(detail.getPrice())
                .currency(detail.getCurrency())
                .tradeDate(detail.getTradeDate())
                .effectiveDate(detail.getEffectiveDate())
                .settlementDate(detail.getSettlementDate())
                .allocationStatus(detail.getAllocationStatus())
                .recordedAt(detail.getRecordedAt())
                .build();
    }

    public BasketOpenLotEntity toOpenLotEntity(String activityId, OpenLot lot) {
        return BasketOpenLotEntity.builder()
                .lotId(lot.getLotId())
                .activityId(activityId)
                .sourceDetailId(lot.getSourceDetailId())
                .sourceTradeId(lot.getSourceTradeId())
                .originalQty(lot.getOriginalQuantity())
                .remainingQty(lot.getRemainingQuantity())
                .costBasis(lot.getCostBasis())
                .currentRefPrice(lot.getCurrentRefPrice())
                .tradeDate(lot.getTradeDate())
                .settlementDate(lot.getSettlementDate())
                .settledQty(lot.getSettledQuantity())
                .acquisitionSequence(lot.getAcquisitionSequence())
                .build();
    }

    public BasketClosingLotEntity toClosingLotEntity(String activityId, ClosingLot lot) {
        return BasketClosingLotEntity.builder()
                .closingLotId(lot.getClosingLotId())
                .activityId(activityId)
                .openedLotId(lot.getOpenedLotId())
                .closingDetailId(lot.getClosingDetailId())
                .closingTradeId(lot.getClosingTradeId())
                .closedQty(lot.getClosedQuantity())
                .closePrice(lot.getClosePrice())
                .costBasis(lot.getCostBasis())
                .realizedPnl(lot.getRealizedPnL())
                .tradeDate(lot.getTradeDate())
                .settlementDate(lot.getSettlementDate())
                .build();
    }

    public BasketSettlementEntity toSettlementEntity(String activityId, BasketSettlement settlement) {
        return BasketSettlementEntity.builder()
                .settlementId(settlement.getSettlementId())
                .activityId(activityId)
                .detailId(settlement.getDetailId())
                .tradeId(settlement.getTradeId())
                .settlementDate(settlement.getSettlementDate())
                .settledQty(settlement.getSettledQuantity())
                .currency(settlement.getCurrency())
                .status(settlement.getStatus())
                .build();
    }

    private BasketActivityDetail toDetail(BasketActivityDetailEntity entity) {
        return BasketActivityDetail.builder()
                .detailId(entity.getDetailId())
                .tradeId(entity.getTradeId())
                .underlier(entity.getUnderlierId() == null ? null : Underlier.equity(entity.getUnderlierId(), entity.getCurrency()))
                .quantity(entity.getQuantity())
                .price(entity.getPrice())
                .currency(entity.getCurrency())
                .tradeDate(entity.getTradeDate())
                .effectiveDate(entity.getEffectiveDate())
                .settlementDate(entity.getSettlementDate())
                .allocationStatus(entity.getAllocationStatus())
                .recordedAt(entity.getRecordedAt())
                .build();
    }

    private OpenLot toOpenLot(BasketOpenLotEntity entity) {
        return OpenLot.builder()
                .lotId(entity.getLotId())
                .sourceDetailId(entity.getSourceDetailId())
                .sourceTradeId(entity.getSourceTradeId())
                .originalQuantity(entity.getOriginalQty())
                .remainingQuantity(entity.getRemainingQty())
                .costBasis(entity.getCostBasis())
                .currentRefPrice(entity.getCurrentRefPrice())
                .tradeDate(entity.getTradeDate())
                .settlementDate(entity.getSettlementDate())
                .settledQuantity(entity.getSettledQty())
                .acquisitionSequence(entity.getAcquisitionSequence() != null ? entity.getAcquisitionSequence() : 0)
                .build();
    }

    private ClosingLot toClosingLot(BasketClosingLotEntity entity) {
        return ClosingLot.builder()
                .closingLotId(entity.getClosingLotId())
                .openedLotId(entity.getOpenedLotId())
                .closingDetailId(entity.getClosingDetailId())
                .closingTradeId(entity.getClosingTradeId())
                .closedQuantity(entity.getClosedQty())
                .closePrice(entity.getClosePrice())
                .costBasis(entity.getCostBasis())
                .realizedPnL(entity.getRealizedPnl())
                .tradeDate(entity.getTradeDate())
                .settlementDate(entity.getSettlementDate())
                .build();
    }

    private BasketSettlement toSettlement(BasketSettlementEntity entity) {
        return BasketSettlement.builder()
                .settlementId(entity.getSettlementId())
                .detailId(entity.getDetailId())
                .tradeId(entity.getTradeId())
                .settlementDate(entity.getSettlementDate())
                .settledQuantity(entity.getSettledQty())
                .currency(entity.getCurrency())
                .status(entity.getStatus())
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

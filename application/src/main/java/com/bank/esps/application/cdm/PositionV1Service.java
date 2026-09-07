package com.bank.esps.application.cdm;

import com.bank.esps.domain.cdm.base.Account;
import com.bank.esps.domain.cdm.base.Book;
import com.bank.esps.domain.cdm.base.Party;
import com.bank.esps.domain.cdm.base.PositionDirection;
import com.bank.esps.domain.cdm.basket.BasketActivity;
import com.bank.esps.domain.cdm.event.DividendInstruction;
import com.bank.esps.domain.cdm.event.PositionTrade;
import com.bank.esps.domain.cdm.event.SettlementInstruction;
import com.bank.esps.domain.cdm.function.ApplyDividendToBasket;
import com.bank.esps.domain.cdm.function.ApplySettlementToBasket;
import com.bank.esps.domain.cdm.function.SaveBasketActivity;
import com.bank.esps.domain.cdm.product.BasketComponent;
import com.bank.esps.domain.cdm.product.FinancialContract;
import com.bank.esps.domain.cdm.product.FinancialProduct;
import com.bank.esps.domain.cdm.product.PayoutLeg;
import com.bank.esps.domain.cdm.product.ProductType;
import com.bank.esps.domain.cdm.product.Underlier;
import com.bank.esps.domain.cdm.product.UnderlierType;
import com.bank.esps.domain.cdm.repository.BasketActivityRepository;
import com.bank.esps.domain.cdm.repository.FinancialContractRepository;
import com.bank.esps.domain.enums.TaxLotMethod;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application facade for the /v1 Position API. Writes the state-saving
 * {@link BasketActivity} aggregate; does not use the event store.
 */
@Service
public class PositionV1Service {

    private final FinancialContractRepository contracts;
    private final BasketActivityRepository positions;
    private final Map<String, Object> idempotency = new ConcurrentHashMap<>();

    public PositionV1Service(FinancialContractRepository contracts, BasketActivityRepository positions) {
        this.contracts = contracts;
        this.positions = positions;
    }

    public UpsertResult<FinancialContract> upsertContract(ContractCommand command) {
        boolean existed = contracts.findByContractId(command.contractId()).isPresent();
        FinancialContract contract = toContract(command);
        contracts.save(contract);
        return new UpsertResult<>(contract, existed);
    }

    public FinancialContract getContract(String contractId) {
        return contracts.findByContractId(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found: " + contractId));
    }

    public List<BasketActivity> positionsForContract(String contractId) {
        getContract(contractId);
        return positions.findByContractId(contractId);
    }

    public BasketActivity getPosition(String positionId) {
        return positions.findByActivityId(positionId)
                .or(() -> positions.findByPositionKey(positionId))
                .orElseThrow(() -> new ResourceNotFoundException("Position not found: " + positionId));
    }

    public BasketActivity findByNaturalKey(String contractId, String securityId, PositionDirection direction) {
        return positions.findOpenByContract(contractId, securityId, direction)
                .or(() -> positions.findLatestByContract(contractId, securityId, direction))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Position not found for " + contractId + "/" + securityId + "/" + direction));
    }

    @SuppressWarnings("unchecked")
    public UpsertResult<List<BasketActivity>> allocate(String contractId, TransactionCommand command,
                                                       String idempotencyKey) {
        FinancialContract contract = getContract(contractId);
        String cacheKey = cacheKey("alloc", contractId, idempotencyKey != null ? idempotencyKey : command.tradeId());
        if (idempotency.containsKey(cacheKey)) {
            return new UpsertResult<>((List<BasketActivity>) idempotency.get(cacheKey), true);
        }
        Optional<BasketActivity> already = positions.findByContractId(contractId).stream()
                .filter(activity -> activity.getDetails().stream()
                        .anyMatch(detail -> command.tradeId().equals(detail.getTradeId())))
                .findFirst();
        if (already.isPresent()) {
            List<BasketActivity> existing = List.of(already.get());
            idempotency.put(cacheKey, existing);
            return new UpsertResult<>(existing, true);
        }
        PositionTrade trade = PositionTrade.builder()
                .tradeId(command.tradeId())
                .accountId(command.accountId() != null ? command.accountId()
                        : (contract.getAccount() != null ? contract.getAccount().getAccountId() : null))
                .bookId(command.bookId() != null ? command.bookId()
                        : (contract.getBook() != null ? contract.getBook().getBookId() : null))
                .instrumentId(command.securityId())
                .currency(command.currency() != null ? command.currency()
                        : (contract.getProduct() != null ? contract.getProduct().getCurrency() : null))
                .contractId(contractId)
                .quantity(command.quantity())
                .price(command.price())
                .tradeDate(command.tradeDate())
                .effectiveDate(command.effectiveDate())
                .settlementDate(command.settlementDate())
                .taxLotMethod(command.taxLotMethod() != null ? command.taxLotMethod() : contract.getTaxLotMethod())
                .build();
        List<BasketActivity> saved = SaveBasketActivity.allocateAndSave(positions, contract, trade);
        idempotency.put(cacheKey, saved);
        return new UpsertResult<>(saved, false);
    }

    public BasketActivity applyDividend(String positionId, DividendInstruction instruction, String idempotencyKey) {
        String cacheKey = cacheKey("div", positionId, idempotencyKey != null ? idempotencyKey : instruction.getDividendId());
        if (idempotency.containsKey(cacheKey)) {
            return (BasketActivity) idempotency.get(cacheKey);
        }
        BasketActivity activity = getPosition(positionId);
        ApplyDividendToBasket.apply(activity, instruction);
        positions.save(activity);
        idempotency.put(cacheKey, activity);
        return activity;
    }

    public BasketActivity applySettlement(String positionId, SettlementInstruction instruction, String idempotencyKey) {
        String cacheKey = cacheKey("stl", positionId,
                idempotencyKey != null ? idempotencyKey
                        : (instruction.getTradeId() != null ? instruction.getTradeId() : instruction.getDetailId()));
        if (idempotency.containsKey(cacheKey)) {
            return (BasketActivity) idempotency.get(cacheKey);
        }
        BasketActivity activity = getPosition(positionId);
        ApplySettlementToBasket.apply(activity, instruction);
        positions.save(activity);
        idempotency.put(cacheKey, activity);
        return activity;
    }

    private FinancialContract toContract(ContractCommand command) {
        Underlier underlier = command.underlier();
        ProductType productType = command.productType() != null ? command.productType() : ProductType.SWAP;
        List<PayoutLeg> payouts = new ArrayList<>();
        if (underlier != null) {
            payouts.add(PayoutLeg.performance(underlier, command.currency()));
        }
        if (productType == ProductType.SWAP) {
            payouts.add(PayoutLeg.funding("SOFR", "1D", command.currency()));
        }
        String qualifier = command.productQualifier();
        if (qualifier == null || qualifier.isBlank()) {
            qualifier = defaultQualifier(productType, underlier);
        }
        FinancialProduct product = FinancialProduct.builder()
                .productType(productType)
                .productQualifier(qualifier)
                .currency(command.currency())
                .underlier(underlier)
                .payouts(payouts)
                .build();
        return FinancialContract.builder()
                .contractId(command.contractId())
                .product(product)
                .party1(command.party1Id() == null ? null : Party.of(command.party1Id()))
                .party2(command.party2Id() == null ? null : Party.of(command.party2Id()))
                .account(command.accountId() == null ? null : Account.of(command.accountId()))
                .book(command.bookId() == null ? null : Book.of(command.bookId()))
                .startDate(command.startDate())
                .endDate(command.endDate())
                .taxLotMethod(command.taxLotMethod() != null ? command.taxLotMethod() : TaxLotMethod.FIFO)
                .build();
    }

    private static String defaultQualifier(ProductType productType, Underlier underlier) {
        if (productType == ProductType.CFD) {
            return "EquityCFD";
        }
        if (underlier != null && underlier.getType() == UnderlierType.INDEX) {
            return "EquityIndexSwap";
        }
        if (underlier != null && underlier.getType() == UnderlierType.BASKET) {
            return "EquityBasketSwap";
        }
        return "EquitySwap";
    }

    private static String cacheKey(String op, String scope, String key) {
        return op + ":" + scope + ":" + (key == null ? "" : key);
    }

    public record UpsertResult<T>(T value, boolean existed) {
    }

    public record ContractCommand(
            String contractId,
            ProductType productType,
            String productQualifier,
            String currency,
            String accountId,
            String bookId,
            String party1Id,
            String party2Id,
            LocalDate startDate,
            LocalDate endDate,
            TaxLotMethod taxLotMethod,
            Underlier underlier
    ) {
    }

    public record TransactionCommand(
            String tradeId,
            String securityId,
            BigDecimal quantity,
            BigDecimal price,
            String currency,
            LocalDate tradeDate,
            LocalDate settlementDate,
            LocalDate effectiveDate,
            String accountId,
            String bookId,
            TaxLotMethod taxLotMethod
    ) {
    }

    public static Underlier underlierFrom(UnderlierType type, String identifier, String identifierScheme,
                                          String instrumentClass, String description, String currency,
                                          String exchange, List<BasketComponent> constituents) {
        UnderlierType resolved = type != null ? type : UnderlierType.EQUITY;
        if (instrumentClass != null && "CIB".equalsIgnoreCase(instrumentClass)) {
            Underlier cib = Underlier.cib(identifier, currency);
            cib.setIdentifierScheme(identifierScheme != null ? identifierScheme : "CIB");
            cib.setDescription(description);
            cib.setExchange(exchange);
            return cib;
        }
        if (resolved == UnderlierType.INDEX) {
            Underlier index = Underlier.index(identifier, currency);
            index.setIdentifierScheme(identifierScheme != null ? identifierScheme : index.getIdentifierScheme());
            index.setInstrumentClass(instrumentClass);
            index.setDescription(description);
            index.setExchange(exchange);
            return index;
        }
        if (resolved == UnderlierType.BASKET) {
            Underlier basket = Underlier.basket(identifier, currency, constituents);
            basket.setIdentifierScheme(identifierScheme);
            basket.setInstrumentClass(instrumentClass);
            basket.setDescription(description);
            basket.setExchange(exchange);
            return basket;
        }
        return Underlier.builder()
                .type(resolved)
                .identifier(identifier)
                .identifierScheme(identifierScheme != null ? identifierScheme : "TICKER")
                .instrumentClass(instrumentClass)
                .description(description)
                .currency(currency)
                .exchange(exchange)
                .constituents(constituents == null ? new ArrayList<>() : new ArrayList<>(constituents))
                .build();
    }

    public static PositionDirection directionOf(String value) {
        return PositionDirection.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}

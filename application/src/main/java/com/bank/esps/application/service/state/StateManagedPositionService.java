package com.bank.esps.application.service.state;

import com.bank.esps.domain.state.ActivityType;
import com.bank.esps.domain.state.DailyLotRow;
import com.bank.esps.domain.state.DailyPositionRow;
import com.bank.esps.domain.state.DailySnapshotPlanner;
import com.bank.esps.domain.state.LifecycleStatus;
import com.bank.esps.domain.state.LotStatus;
import com.bank.esps.domain.state.ManagedLot;
import com.bank.esps.domain.state.PositionBook;
import com.bank.esps.domain.state.StateActivity;
import com.bank.esps.domain.state.StateHistory;
import com.bank.esps.domain.state.UpiChange;
import com.bank.esps.infrastructure.persistence.entity.state.SmLotDailyEntity;
import com.bank.esps.infrastructure.persistence.entity.state.SmLotEntity;
import com.bank.esps.infrastructure.persistence.entity.state.SmPositionDailyEntity;
import com.bank.esps.infrastructure.persistence.entity.state.SmPositionEntity;
import com.bank.esps.infrastructure.persistence.entity.state.SmPositionHistoryEntity;
import com.bank.esps.infrastructure.persistence.entity.state.SmTradeEntity;
import com.bank.esps.infrastructure.persistence.entity.state.SmUpiHistoryEntity;
import com.bank.esps.infrastructure.persistence.repository.state.SmLotDailyRepository;
import com.bank.esps.infrastructure.persistence.repository.state.SmLotRepository;
import com.bank.esps.infrastructure.persistence.repository.state.SmPositionDailyRepository;
import com.bank.esps.infrastructure.persistence.repository.state.SmPositionHistoryRepository;
import com.bank.esps.infrastructure.persistence.repository.state.SmPositionRepository;
import com.bank.esps.infrastructure.persistence.repository.state.SmTradeRepository;
import com.bank.esps.infrastructure.persistence.repository.state.SmUpiHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Updates the position and its lots in place. Trades are stored as facts.
 * This path does not append to the event store and does not rebuild state by replaying it.
 */
@Service
public class StateManagedPositionService {

    private final SmPositionRepository positionRepository;
    private final SmLotRepository lotRepository;
    private final SmTradeRepository tradeRepository;
    private final SmPositionDailyRepository positionDailyRepository;
    private final SmLotDailyRepository lotDailyRepository;
    private final SmPositionHistoryRepository historyRepository;
    private final SmUpiHistoryRepository upiHistoryRepository;
    private final AtomicLong appliedSequence = new AtomicLong(System.currentTimeMillis());

    public StateManagedPositionService(SmPositionRepository positionRepository,
                                       SmLotRepository lotRepository,
                                       SmTradeRepository tradeRepository,
                                       SmPositionDailyRepository positionDailyRepository,
                                       SmLotDailyRepository lotDailyRepository,
                                       SmPositionHistoryRepository historyRepository,
                                       SmUpiHistoryRepository upiHistoryRepository) {
        this.positionRepository = positionRepository;
        this.lotRepository = lotRepository;
        this.tradeRepository = tradeRepository;
        this.positionDailyRepository = positionDailyRepository;
        this.lotDailyRepository = lotDailyRepository;
        this.historyRepository = historyRepository;
        this.upiHistoryRepository = upiHistoryRepository;
    }

    @Transactional
    public PositionView apply(String positionKey, StateActivity activity) {
        if (tradeRepository.existsByTradeId(activity.tradeId())) {
            throw new IllegalStateException("Trade already applied: " + activity.tradeId());
        }
        SmPositionEntity existing = positionRepository.findById(positionKey).orElse(null);
        if (existing != null && existing.getAsOfDate() != null
                && activity.effectiveDate().isBefore(existing.getAsOfDate())) {
            tradeRepository.saveAndFlush(toTrade(positionKey, activity));
            return rebuild(positionKey, activity.effectiveDate());
        }
        PositionBook book = existing == null
                ? PositionBook.create(positionKey)
                : loadBook(existing, lotRepository.findByPositionKey(positionKey));
        book.apply(activity);
        tradeRepository.save(toTrade(positionKey, activity));
        saveBook(existing, book);
        appendAudit(positionKey, book);
        return PositionView.from(book);
    }

    @Transactional(readOnly = true)
    public PositionView get(String positionKey) {
        return positionRepository.findById(positionKey)
                .map(PositionView::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<LotView> lots(String positionKey, LocalDate businessDate) {
        if (businessDate == null) {
            return lotRepository.findByPositionKey(positionKey).stream().map(LotView::from).toList();
        }
        return lotDailyRepository.findByPositionKeyAndBusinessDate(positionKey, businessDate)
                .stream()
                .map(LotView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TradeView> trades(String positionKey) {
        return tradeRepository.findByPositionKeyOrderByEffectiveDateAscAppliedAtAsc(positionKey)
                .stream()
                .map(TradeView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TradeView trade(String tradeId) {
        return tradeRepository.findById(tradeId).map(TradeView::from).orElse(null);
    }

    /**
     * Writes one region's daily snapshot. Positions that traded or reset on {@code businessDate}
     * are taken from live state. Every other active position is copied from the previous business date.
     */
    @Transactional
    public int roll(String region, LocalDate businessDate, LocalDate previousBusinessDate) {
        Map<String, PositionBook> dirty = new HashMap<>();
        for (SmTradeEntity trade : tradeRepository.findByRegionAndEffectiveDate(region, businessDate)) {
            dirty.computeIfAbsent(trade.getPositionKey(), this::loadLiveBook);
        }
        List<DailyPositionRow> previousPositions = positionDailyRepository
                .findByRegionAndBusinessDate(region, previousBusinessDate)
                .stream()
                .map(StateManagedPositionService::toDailyPosition)
                .toList();
        List<DailyLotRow> previousOpenLots = lotDailyRepository
                .findByRegionAndBusinessDateAndStatus(region, previousBusinessDate, LotStatus.OPEN)
                .stream()
                .map(StateManagedPositionService::toDailyLot)
                .toList();
        DailySnapshotPlanner.SnapshotBatch batch = DailySnapshotPlanner.plan(
                businessDate, previousPositions, previousOpenLots, dirty);
        positionDailyRepository.deleteForRegionDay(region, businessDate);
        lotDailyRepository.deleteForRegionDay(region, businessDate);
        positionDailyRepository.saveAll(batch.positions().stream().map(StateManagedPositionService::toPositionDaily).toList());
        lotDailyRepository.saveAll(batch.lots().stream().map(StateManagedPositionService::toLotDaily).toList());
        return batch.lots().size();
    }

    /**
     * Rebuilds one position from the last daily snapshot before {@code fromDate},
     * then replays stored trades from that date forward.
     */
    @Transactional
    public PositionView rebuild(String positionKey, LocalDate fromDate) {
        SmPositionEntity position = positionRepository.findById(positionKey)
                .orElseThrow(() -> new IllegalArgumentException("Unknown position " + positionKey));
        SmPositionDailyEntity prior = positionDailyRepository
                .findFirstByPositionKeyAndBusinessDateLessThanOrderByBusinessDateDesc(positionKey, fromDate)
                .orElse(null);
        if (prior == null && tradeRepository.existsByPositionKeyAndEffectiveDateLessThan(positionKey, fromDate)) {
            throw new IllegalStateException("No daily snapshot before " + fromDate + " for " + positionKey);
        }
        List<ManagedLot> startingLots = prior == null
                ? List.of()
                : lotDailyRepository.findByPositionKeyAndBusinessDateAndStatus(positionKey, prior.getBusinessDate(), LotStatus.OPEN)
                .stream()
                .map(row -> lotFromDaily(row, lotRepository.findById(row.getLotId()).orElse(null)))
                .toList();
        BigDecimal priorRealized = prior == null ? BigDecimal.ZERO : prior.getRealizedPnl();
        BigDecimal openRealized = startingLots.stream()
                .map(ManagedLot::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PositionBook book = prior == null
                ? PositionBook.create(positionKey)
                : PositionBook.restore(
                positionKey,
                prior.getRegion(),
                position.getAccount(),
                position.getInstrument(),
                position.getCurrency(),
                prior.getStatus(),
                prior.getUpi(),
                position.getNextLotSeq(),
                prior.getBusinessDate(),
                startingLots,
                priorRealized.subtract(openRealized));
        List<StateActivity> activities = tradeRepository
                .findByPositionKeyAndEffectiveDateGreaterThanEqualOrderByEffectiveDateAscAppliedAtAsc(positionKey, fromDate)
                .stream()
                .map(StateManagedPositionService::toActivity)
                .toList();
        if (activities.isEmpty()) {
            return PositionView.from(position);
        }
        List<PositionBook> days = book.replay(activities);
        historyRepository.deleteFrom(positionKey, fromDate);
        upiHistoryRepository.deleteFrom(positionKey, fromDate);
        positionDailyRepository.deleteFrom(positionKey, fromDate);
        lotDailyRepository.deleteFrom(positionKey, fromDate);
        SmPositionEntity managed = positionRepository.findById(positionKey).orElseThrow();
        replaceRewrittenLots(positionKey, fromDate, book);
        saveBook(managed, book);
        appendAudit(positionKey, book);
        for (PositionBook day : days) {
            LocalDate date = day.getAsOfDate();
            positionDailyRepository.save(toPositionDaily(DailyPositionRow.from(day, date)));
            lotDailyRepository.saveAll(day.lotsForSnapshot(date).stream()
                    .map(lot -> toLotDaily(DailyLotRow.from(day, lot, date)))
                    .toList());
        }
        return PositionView.from(book);
    }

    private void replaceRewrittenLots(String positionKey, LocalDate fromDate, PositionBook book) {
        List<SmLotEntity> drop = lotRepository.findByPositionKey(positionKey).stream()
                .filter(lot -> lot.getStatus() == LotStatus.OPEN
                        || (lot.getClosedOn() != null && !lot.getClosedOn().isBefore(fromDate)))
                .toList();
        lotRepository.deleteAll(drop);
        lotRepository.flush();
        saveLots(positionKey, book);
    }

    private PositionBook loadLiveBook(String positionKey) {
        SmPositionEntity position = positionRepository.findById(positionKey)
                .orElseThrow(() -> new IllegalStateException("Missing position " + positionKey));
        return loadBook(position, lotRepository.findByPositionKey(positionKey));
    }

    private PositionBook loadBook(SmPositionEntity position, List<SmLotEntity> lots) {
        BigDecimal lotRealized = lots.stream()
                .map(SmLotEntity::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return PositionBook.restore(
                position.getPositionKey(),
                position.getRegion(),
                position.getAccount(),
                position.getInstrument(),
                position.getCurrency(),
                position.getStatus(),
                position.getUpi(),
                position.getNextLotSeq(),
                position.getAsOfDate(),
                lots.stream().map(StateManagedPositionService::toLot).toList(),
                position.getRealizedPnl().subtract(lotRealized));
    }

    private void saveBook(SmPositionEntity existing, PositionBook book) {
        SmPositionEntity position = existing == null ? new SmPositionEntity() : existing;
        position.setPositionKey(book.getPositionKey());
        position.setRegion(book.getRegion());
        position.setAccount(book.getAccount());
        position.setInstrument(book.getInstrument());
        position.setCurrency(book.getCurrency());
        position.setStatus(book.getStatus());
        position.setUpi(book.getUpi());
        position.setTotalQty(book.totalQty());
        position.setAvgPrice(book.avgPrice());
        position.setOpenLotCount(book.openLotCount());
        position.setRealizedPnl(book.realizedPnl());
        position.setAsOfDate(book.getAsOfDate());
        position.setNextLotSeq(book.getNextLotSeq());
        positionRepository.save(position);
        saveLots(book.getPositionKey(), book);
    }

    private void saveLots(String positionKey, PositionBook book) {
        Map<String, SmLotEntity> current = new HashMap<>();
        for (SmLotEntity entity : lotRepository.findByPositionKey(positionKey)) {
            current.put(entity.getLotId(), entity);
        }
        for (ManagedLot lot : book.getLots()) {
            SmLotEntity entity = current.get(lot.getLotId());
            if (entity == null) {
                entity = new SmLotEntity();
                entity.setLotId(lot.getLotId());
                entity.setPositionKey(positionKey);
            }
            copyLot(lot, entity);
            lotRepository.save(entity);
        }
    }

    private void appendAudit(String positionKey, PositionBook book) {
        for (StateHistory history : book.getHistory()) {
            SmPositionHistoryEntity entity = new SmPositionHistoryEntity();
            entity.setHistoryId(UUID.randomUUID().toString());
            entity.setPositionKey(positionKey);
            entity.setTradeId(history.tradeId());
            entity.setEffectiveDate(history.effectiveDate());
            entity.setQtyBefore(history.qtyBefore());
            entity.setQtyAfter(history.qtyAfter());
            entity.setStatusBefore(history.statusBefore());
            entity.setStatusAfter(history.statusAfter());
            entity.setOpenPriceAfter(history.openPriceAfter());
            historyRepository.save(entity);
        }
        for (UpiChange change : book.getUpiChanges()) {
            SmUpiHistoryEntity entity = new SmUpiHistoryEntity();
            entity.setHistoryId(UUID.randomUUID().toString());
            entity.setPositionKey(positionKey);
            entity.setUpi(change.upi());
            entity.setPreviousUpi(change.previousUpi());
            entity.setChangeType(change.changeType());
            entity.setTradeId(change.tradeId());
            entity.setEffectiveDate(change.effectiveDate());
            upiHistoryRepository.save(entity);
        }
    }

    private static void copyLot(ManagedLot lot, SmLotEntity entity) {
        entity.setParentLotId(lot.getParentLotId());
        entity.setOpeningTradeId(lot.getOpeningTradeId());
        entity.setClosingTradeId(lot.getClosingTradeId());
        entity.setOriginalQty(lot.getOriginalQty());
        entity.setRemainingQty(lot.getRemainingQty());
        entity.setOpenPrice(lot.getOpenPrice());
        entity.setClosePrice(lot.getClosePrice());
        entity.setRealizedPnl(lot.getRealizedPnl());
        entity.setStatus(lot.getStatus());
        entity.setEffectiveDate(lot.getEffectiveDate());
        entity.setSettlementDate(lot.getSettlementDate());
        entity.setClosedOn(lot.getClosedOn());
    }

    private static ManagedLot toLot(SmLotEntity entity) {
        return new ManagedLot(
                entity.getLotId(),
                entity.getParentLotId(),
                entity.getOpeningTradeId(),
                entity.getOriginalQty(),
                entity.getRemainingQty(),
                entity.getOpenPrice(),
                entity.getRealizedPnl(),
                entity.getStatus(),
                entity.getEffectiveDate(),
                entity.getSettlementDate(),
                entity.getClosedOn(),
                entity.getClosingTradeId(),
                entity.getClosePrice());
    }

    private static ManagedLot lotFromDaily(SmLotDailyEntity daily, SmLotEntity live) {
        if (live == null) {
            return new ManagedLot(
                    daily.getLotId(), null, daily.getLotId(), daily.getOriginalQty(), daily.getRemainingQty(),
                    daily.getOpenPrice(), daily.getRealizedPnl(), daily.getStatus(), daily.getBusinessDate(),
                    daily.getSettlementDate(), null, null, null);
        }
        return new ManagedLot(
                live.getLotId(),
                live.getParentLotId(),
                live.getOpeningTradeId(),
                daily.getOriginalQty(),
                daily.getRemainingQty(),
                daily.getOpenPrice(),
                daily.getRealizedPnl(),
                LotStatus.OPEN,
                live.getEffectiveDate(),
                live.getSettlementDate(),
                null,
                null,
                null);
    }

    private SmTradeEntity toTrade(String positionKey, StateActivity activity) {
        SmTradeEntity entity = new SmTradeEntity();
        entity.setTradeId(activity.tradeId());
        entity.setPositionKey(positionKey);
        entity.setActivityType(activity.type());
        entity.setQuantity(activity.quantity());
        entity.setPrice(activity.price() != null ? activity.price()
                : activity.quantity() != null ? activity.quantity() : BigDecimal.ZERO);
        entity.setEffectiveDate(activity.effectiveDate());
        entity.setSettlementDate(activity.settlementDate());
        entity.setRegion(activity.region());
        entity.setAppliedAt(OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(appliedSequence.incrementAndGet()), ZoneOffset.UTC));
        entity.setAccount(activity.account());
        entity.setInstrument(activity.instrument());
        entity.setCurrency(activity.currency());
        return entity;
    }

    private static StateActivity toActivity(SmTradeEntity entity) {
        return new StateActivity(
                entity.getTradeId(),
                entity.getActivityType(),
                entity.getQuantity(),
                entity.getPrice(),
                entity.getEffectiveDate(),
                entity.getSettlementDate(),
                entity.getRegion(),
                entity.getAccount(),
                entity.getInstrument(),
                entity.getCurrency());
    }

    private static DailyPositionRow toDailyPosition(SmPositionDailyEntity entity) {
        return new DailyPositionRow(
                entity.getPositionKey(), entity.getRegion(), entity.getBusinessDate(),
                entity.getTotalQty(), entity.getAvgPrice(), entity.getOpenLotCount(),
                entity.getStatus(), entity.getUpi(), entity.getRealizedPnl());
    }

    private static DailyLotRow toDailyLot(SmLotDailyEntity entity) {
        return new DailyLotRow(
                entity.getPositionKey(), entity.getRegion(), entity.getLotId(), entity.getBusinessDate(),
                entity.getRemainingQty(), entity.getTradeableQty(), entity.getOriginalQty(),
                entity.getOpenPrice(), entity.getStatus(), entity.getSettlementDate(), entity.getRealizedPnl());
    }

    private static SmPositionDailyEntity toPositionDaily(DailyPositionRow row) {
        SmPositionDailyEntity entity = new SmPositionDailyEntity();
        entity.setPositionKey(row.positionKey());
        entity.setBusinessDate(row.businessDate());
        entity.setRegion(row.region());
        entity.setTotalQty(row.totalQty());
        entity.setAvgPrice(row.avgPrice());
        entity.setOpenLotCount(row.openLotCount());
        entity.setStatus(row.status());
        entity.setUpi(row.upi());
        entity.setRealizedPnl(row.realizedPnl());
        return entity;
    }

    private static SmLotDailyEntity toLotDaily(DailyLotRow row) {
        SmLotDailyEntity entity = new SmLotDailyEntity();
        entity.setPositionKey(row.positionKey());
        entity.setLotId(row.lotId());
        entity.setBusinessDate(row.businessDate());
        entity.setRegion(row.region());
        entity.setRemainingQty(row.remainingQty());
        entity.setTradeableQty(row.tradeableQty());
        entity.setOriginalQty(row.originalQty());
        entity.setOpenPrice(row.openPrice());
        entity.setStatus(row.status());
        entity.setSettlementDate(row.settlementDate());
        entity.setRealizedPnl(row.realizedPnl());
        return entity;
    }

    public record PositionView(
            String positionKey,
            String region,
            String account,
            String instrument,
            String currency,
            LifecycleStatus status,
            String upi,
            BigDecimal totalQty,
            BigDecimal avgPrice,
            int openLotCount,
            BigDecimal realizedPnl,
            LocalDate asOfDate) {

        static PositionView from(PositionBook book) {
            return new PositionView(
                    book.getPositionKey(), book.getRegion(), book.getAccount(), book.getInstrument(),
                    book.getCurrency(), book.getStatus(), book.getUpi(), book.totalQty(), book.avgPrice(),
                    book.openLotCount(), book.realizedPnl(), book.getAsOfDate());
        }

        static PositionView from(SmPositionEntity entity) {
            return new PositionView(
                    entity.getPositionKey(), entity.getRegion(), entity.getAccount(), entity.getInstrument(),
                    entity.getCurrency(), entity.getStatus(), entity.getUpi(), entity.getTotalQty(),
                    entity.getAvgPrice(), entity.getOpenLotCount(), entity.getRealizedPnl(), entity.getAsOfDate());
        }
    }

    public record LotView(
            String lotId,
            String parentLotId,
            BigDecimal originalQty,
            BigDecimal remainingQty,
            BigDecimal openPrice,
            BigDecimal tradeableQty,
            BigDecimal realizedPnl,
            LotStatus status,
            LocalDate closedOn) {

        static LotView from(SmLotEntity entity) {
            return new LotView(entity.getLotId(), entity.getParentLotId(), entity.getOriginalQty(),
                    entity.getRemainingQty(), entity.getOpenPrice(), null, entity.getRealizedPnl(),
                    entity.getStatus(), entity.getClosedOn());
        }

        static LotView from(SmLotDailyEntity entity) {
            return new LotView(entity.getLotId(), null, entity.getOriginalQty(), entity.getRemainingQty(),
                    entity.getOpenPrice(), entity.getTradeableQty(), entity.getRealizedPnl(),
                    entity.getStatus(), null);
        }
    }

    public record TradeView(
            String tradeId,
            String positionKey,
            ActivityType type,
            BigDecimal quantity,
            BigDecimal price,
            LocalDate effectiveDate,
            LocalDate settlementDate,
            String region) {

        static TradeView from(SmTradeEntity entity) {
            return new TradeView(entity.getTradeId(), entity.getPositionKey(), entity.getActivityType(),
                    entity.getQuantity(), entity.getPrice(), entity.getEffectiveDate(),
                    entity.getSettlementDate(), entity.getRegion());
        }
    }

    public record RollRequest(LocalDate businessDate, LocalDate previousBusinessDate) {
    }

    public record ActivityRequest(
            String tradeId,
            ActivityType type,
            BigDecimal quantity,
            BigDecimal price,
            LocalDate effectiveDate,
            LocalDate settlementDate,
            String region,
            String account,
            String instrument,
            String currency) {

        public StateActivity toActivity() {
            return new StateActivity(tradeId, type, quantity, price, effectiveDate, settlementDate,
                    region, account, instrument, currency);
        }
    }
}

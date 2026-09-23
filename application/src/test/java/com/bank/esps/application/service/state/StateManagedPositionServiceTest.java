package com.bank.esps.application.service.state;

import com.bank.esps.domain.state.ActivityType;
import com.bank.esps.domain.state.LotStatus;
import com.bank.esps.infrastructure.persistence.repository.state.SmLotDailyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.autoconfigure.domain.EntityScan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@Import(StateManagedPositionService.class)
@EntityScan(basePackages = "com.bank.esps.infrastructure.persistence.entity.state")
@EnableJpaRepositories(basePackages = "com.bank.esps.infrastructure.persistence.repository.state")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class StateManagedPositionServiceTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class StateTestApplication {
    }

    private static final LocalDate MAR_17 = LocalDate.of(2026, 3, 17);
    private static final LocalDate MAR_18 = LocalDate.of(2026, 3, 18);
    private static final LocalDate MAR_19 = LocalDate.of(2026, 3, 19);
    private static final LocalDate MAR_20 = LocalDate.of(2026, 3, 20);

    @Autowired
    private StateManagedPositionService service;
    @Autowired
    private SmLotDailyRepository lotDailyRepository;

    @Test
    void closeResetAndRegionalRollCarryTheResetPrice() {
        apply("POS-EQ-1", "T-1", ActivityType.OPEN, "100", "10", MAR_18, MAR_20, "APAC");
        apply("POS-EQ-1", "T-440", ActivityType.CLOSE, "50", "12", MAR_18, MAR_20, "APAC");
        apply("POS-EQ-1", "R-1", ActivityType.RESET, null, "11.50", MAR_18, null, "APAC");

        service.roll("APAC", MAR_18, MAR_17);
        List<StateManagedPositionService.LotView> closeDay = service.lots("POS-EQ-1", MAR_18);
        assertEquals(2, closeDay.size());
        StateManagedPositionService.LotView open = closeDay.stream()
                .filter(lot -> lot.status() == LotStatus.OPEN)
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("11.50").compareTo(open.openPrice()));
        assertEquals(0, BigDecimal.ZERO.compareTo(open.tradeableQty()));

        service.roll("APAC", MAR_19, MAR_18);
        List<StateManagedPositionService.LotView> carried = service.lots("POS-EQ-1", MAR_19);
        assertEquals(1, carried.size());
        assertEquals("POS-EQ-1-L1", carried.get(0).lotId());
        assertEquals(0, new BigDecimal("50").compareTo(carried.get(0).remainingQty()));
        assertEquals(0, new BigDecimal("11.50").compareTo(carried.get(0).openPrice()));
        assertEquals(LotStatus.OPEN, carried.get(0).status());

        service.roll("APAC", MAR_20, MAR_19);
        StateManagedPositionService.LotView settled = service.lots("POS-EQ-1", MAR_20).get(0);
        assertEquals(0, new BigDecimal("50").compareTo(settled.tradeableQty()));

        List<StateManagedPositionService.TradeView> trades = service.trades("POS-EQ-1");
        assertEquals(List.of("T-1", "T-440", "R-1"), trades.stream().map(StateManagedPositionService.TradeView::tradeId).toList());
        assertEquals(ActivityType.CLOSE, service.trade("T-440").type());
        assertEquals("POS-EQ-1", service.trade("T-440").positionKey());
        assertNull(service.trade("missing"));
    }

    @Test
    void topUpKeepsUpiAndFullCloseThenReopenMintsANewOne() {
        apply("POS-EQ-1", "T-1", ActivityType.OPEN, "100", "10", MAR_18, MAR_18, "APAC");
        apply("POS-EQ-1", "T-2", ActivityType.OPEN, "25", "11", MAR_18, MAR_18, "APAC");
        assertEquals("T-1", service.get("POS-EQ-1").upi());
        assertEquals(2, service.get("POS-EQ-1").openLotCount());

        apply("POS-EQ-1", "T-3", ActivityType.CLOSE, "125", "12", MAR_19, MAR_19, "APAC");
        service.roll("APAC", MAR_19, MAR_18);
        assertEquals("TERMINATED", service.get("POS-EQ-1").status().name());
        assertTrue(service.lots("POS-EQ-1", MAR_19).stream().allMatch(lot -> lot.status() == LotStatus.CLOSED));

        service.roll("APAC", MAR_20, MAR_19);
        assertTrue(service.lots("POS-EQ-1", MAR_20).isEmpty());

        apply("POS-EQ-1", "T-4", ActivityType.OPEN, "80", "14", MAR_20, MAR_20, "APAC");
        assertEquals("T-4", service.get("POS-EQ-1").upi());
        assertEquals(0, new BigDecimal("80").compareTo(service.get("POS-EQ-1").totalQty()));
    }

    @Test
    void backdatedOpenKeepsTheOriginalUpi() {
        apply("POS-EQ-1", "T-1", ActivityType.OPEN, "100", "10", MAR_18, MAR_18, "APAC");
        apply("POS-EQ-1", "T-2", ActivityType.CLOSE, "100", "12", MAR_19, MAR_19, "APAC");
        apply("POS-EQ-1", "T-3", ActivityType.OPEN, "40", "20", MAR_20, MAR_20, "APAC");
        assertEquals("T-3", service.get("POS-EQ-1").upi());

        apply("POS-EQ-1", "T-BACK", ActivityType.OPEN, "50", "10", MAR_18, MAR_18, "APAC");

        StateManagedPositionService.PositionView position = service.get("POS-EQ-1");
        assertEquals("T-1", position.upi());
        assertEquals("ACTIVE", position.status().name());
        assertEquals(0, new BigDecimal("90").compareTo(position.totalQty()));
        assertEquals(4, service.trades("POS-EQ-1").size());
    }

    @Test
    void duplicateTradeIsRejectedAndAnotherRegionIsNotRolled() {
        apply("POS-EQ-1", "T-1", ActivityType.OPEN, "100", "10", MAR_18, MAR_18, "APAC");
        assertThrows(IllegalStateException.class,
                () -> apply("POS-EQ-1", "T-1", ActivityType.OPEN, "100", "10", MAR_18, MAR_18, "APAC"));

        apply("POS-NY", "T-NY", ActivityType.OPEN, "30", "8", MAR_18, MAR_18, "NY");
        service.roll("APAC", MAR_18, MAR_17);

        assertTrue(lotDailyRepository.findByRegionAndBusinessDateAndStatus("NY", MAR_18, LotStatus.OPEN).isEmpty());
        assertEquals(1, lotDailyRepository.findByRegionAndBusinessDateAndStatus("APAC", MAR_18, LotStatus.OPEN).size());
    }

    @Test
    void resetSplitAndDividendCarryTheAdjustedOpenLot() {
        apply("POS-EQ-1", "T-1", ActivityType.OPEN, "100", "10", MAR_18, MAR_18, "APAC");
        apply("POS-EQ-1", "T-2", ActivityType.CLOSE, "40", "12", MAR_18, MAR_18, "APAC");
        apply("POS-EQ-1", "S-1", ActivityType.STOCK_SPLIT, "2", null, MAR_18, null, "APAC");
        apply("POS-EQ-1", "D-1", ActivityType.DIVIDEND, null, "0.50", MAR_18, null, "APAC");
        apply("POS-EQ-1", "R-1", ActivityType.RESET, null, "6", MAR_18, null, "APAC");

        service.roll("APAC", MAR_18, MAR_17);
        service.roll("APAC", MAR_19, MAR_18);

        StateManagedPositionService.LotView carried = service.lots("POS-EQ-1", MAR_19).stream()
                .filter(lot -> lot.status() == LotStatus.OPEN)
                .findFirst()
                .orElseThrow();
        assertEquals(0, new BigDecimal("120").compareTo(carried.remainingQty()));
        assertEquals(0, new BigDecimal("6").compareTo(carried.openPrice()));
        assertEquals("T-1", service.get("POS-EQ-1").upi());
        assertEquals(List.of("T-1", "T-2", "S-1", "D-1", "R-1"),
                service.trades("POS-EQ-1").stream().map(StateManagedPositionService.TradeView::tradeId).toList());
        assertEquals(ActivityType.STOCK_SPLIT, service.trade("S-1").type());
        assertEquals(ActivityType.DIVIDEND, service.trade("D-1").type());
        assertEquals(ActivityType.RESET, service.trade("R-1").type());
    }

    private void apply(String positionKey, String tradeId, ActivityType type, String quantity, String price,
                       LocalDate effective, LocalDate settlement, String region) {
        service.apply(positionKey, new com.bank.esps.domain.state.StateActivity(
                tradeId,
                type,
                quantity == null ? null : new BigDecimal(quantity),
                price == null ? null : new BigDecimal(price),
                effective,
                settlement,
                region,
                "A1",
                "AAPL",
                "USD"));
    }
}

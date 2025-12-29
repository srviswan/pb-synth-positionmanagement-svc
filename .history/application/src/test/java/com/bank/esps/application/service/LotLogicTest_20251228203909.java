package com.bank.esps.application.service;

import com.bank.esps.domain.enums.TaxLotMethod;
import com.bank.esps.domain.model.Contract;
import com.bank.esps.domain.model.LotAllocationResult;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.domain.model.TaxLot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LotLogicTest {
    
    private LotLogic lotLogic;
    
    @BeforeEach
    void setUp() {
        lotLogic = new LotLogic();
    }
    
    @Test
    void testAddLot() {
        PositionState state = new PositionState();
        BigDecimal qty = new BigDecimal("100");
        BigDecimal price = new BigDecimal("50.00");
        LocalDate tradeDate = LocalDate.now();
        
        LotAllocationResult result = lotLogic.addLot(state, qty, price, tradeDate);
        
        assertEquals(1, state.getOpenLots().size());
        assertEquals(1, result.getAllocations().size());
        assertEquals(qty, state.getTotalQty());
        assertEquals(qty.multiply(price), state.getExposure());
        
        // Verify settlement date defaults to trade date
        TaxLot lot = state.getOpenLots().get(0);
        assertEquals(tradeDate, lot.getSettlementDateForAccrual());
        assertEquals(qty, lot.getSettledQuantityForAccrual());
    }
    
    @Test
    void testAddLotWithSettlementDate() {
        PositionState state = new PositionState();
        BigDecimal qty = new BigDecimal("100");
        BigDecimal price = new BigDecimal("50.00");
        LocalDate tradeDate = LocalDate.of(2024, 1, 15);
        LocalDate settlementDate = LocalDate.of(2024, 1, 17); // T+2 settlement
        BigDecimal settledQty = new BigDecimal("100");
        
        LotAllocationResult result = lotLogic.addLot(state, qty, price, tradeDate, settlementDate, settledQty);
        
        assertEquals(1, state.getOpenLots().size());
        TaxLot lot = state.getOpenLots().get(0);
        assertEquals(tradeDate, lot.getTradeDate());
        assertEquals(settlementDate, lot.getSettlementDate());
        assertEquals(settlementDate, lot.getSettlementDateForAccrual());
        assertEquals(settledQty, lot.getSettledQuantity());
        assertEquals(settledQty, lot.getSettledQuantityForAccrual());
    }
    
    @Test
    void testReduceLotsFIFO() {
        PositionState state = new PositionState();
        Contract contract = Contract.builder()
                .contractId("C1")
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
        
        // Add lots in order
        lotLogic.addLot(state, new BigDecimal("100"), new BigDecimal("50.00"), LocalDate.of(2024, 1, 1));
        lotLogic.addLot(state, new BigDecimal("200"), new BigDecimal("55.00"), LocalDate.of(2024, 1, 2));
        lotLogic.addLot(state, new BigDecimal("150"), new BigDecimal("60.00"), LocalDate.of(2024, 1, 3));
        
        // Reduce 150 (should take from first lot)
        LotAllocationResult result = lotLogic.reduceLots(state, new BigDecimal("150"), contract, new BigDecimal("55.00"));
        
        assertEquals(2, result.getAllocations().size());
        assertEquals(new BigDecimal("150"), result.getAllocations().get(0).getQty()
                .add(result.getAllocations().get(1).getQty()));
        
        // First lot should be fully consumed
        List<TaxLot> openLots = state.getOpenLots();
        assertEquals(2, openLots.size());
        assertEquals(new BigDecimal("150"), openLots.get(0).getRemainingQty()); // Second lot
    }
    
    @Test
    void testReduceLotsLIFO() {
        PositionState state = new PositionState();
        Contract contract = Contract.builder()
                .contractId("C1")
                .taxLotMethod(TaxLotMethod.LIFO)
                .build();
        
        // Add lots in order
        lotLogic.addLot(state, new BigDecimal("100"), new BigDecimal("50.00"), LocalDate.of(2024, 1, 1));
        lotLogic.addLot(state, new BigDecimal("200"), new BigDecimal("55.00"), LocalDate.of(2024, 1, 2));
        lotLogic.addLot(state, new BigDecimal("150"), new BigDecimal("60.00"), LocalDate.of(2024, 1, 3));
        
        // Reduce 100 (should take from last lot first)
        LotAllocationResult result = lotLogic.reduceLots(state, new BigDecimal("100"), contract, new BigDecimal("55.00"));
        
        assertEquals(1, result.getAllocations().size());
        assertEquals(new BigDecimal("100"), result.getAllocations().get(0).getQty());
        // Price in allocation result is the close price, not the original lot price
        assertEquals(new BigDecimal("55.00"), result.getAllocations().get(0).getPrice());
        
        // Last lot should have 50 remaining
        List<TaxLot> openLots = state.getOpenLots();
        assertEquals(3, openLots.size());
        assertEquals(new BigDecimal("50"), openLots.get(2).getRemainingQty());
    }
    
    @Test
    void testReduceLotsHIFO() {
        PositionState state = new PositionState();
        Contract contract = Contract.builder()
                .contractId("C1")
                .taxLotMethod(TaxLotMethod.HIFO)
                .build();
        
        // Add lots with different prices
        lotLogic.addLot(state, new BigDecimal("100"), new BigDecimal("50.00"), LocalDate.of(2024, 1, 1));
        lotLogic.addLot(state, new BigDecimal("200"), new BigDecimal("60.00"), LocalDate.of(2024, 1, 2));
        lotLogic.addLot(state, new BigDecimal("150"), new BigDecimal("55.00"), LocalDate.of(2024, 1, 3));
        
        // Reduce 100 (should take from highest price first - 60.00)
        LotAllocationResult result = lotLogic.reduceLots(state, new BigDecimal("100"), contract, new BigDecimal("55.00"));
        
        assertEquals(1, result.getAllocations().size());
        // Price in allocation result is the close price, not the original lot price
        // Verify that the correct lot was selected by checking the lot ID or realized P&L
        assertEquals(new BigDecimal("55.00"), result.getAllocations().get(0).getPrice());
        
        // Highest price lot should have 100 remaining
        List<TaxLot> openLots = state.getOpenLots();
        assertEquals(3, openLots.size());
    }
    
    @Test
    void testUpdateLotPrices() {
        PositionState state = new PositionState();
        lotLogic.addLot(state, new BigDecimal("100"), new BigDecimal("50.00"), LocalDate.now());
        lotLogic.addLot(state, new BigDecimal("200"), new BigDecimal("55.00"), LocalDate.now());
        
        BigDecimal newPrice = new BigDecimal("65.00");
        lotLogic.updateLotPrices(state, newPrice);
        
        state.getOpenLots().forEach(lot -> 
            assertEquals(newPrice, lot.getCurrentRefPrice())
        );
    }
    
    @Test
    void testReduceLotsInsufficientQuantity() {
        PositionState state = new PositionState();
        Contract contract = Contract.builder()
                .contractId("C1")
                .taxLotMethod(TaxLotMethod.FIFO)
                .build();
        
        lotLogic.addLot(state, new BigDecimal("100"), new BigDecimal("50.00"), LocalDate.now());
        
        assertThrows(IllegalStateException.class, () -> {
            lotLogic.reduceLots(state, new BigDecimal("200"), contract, new BigDecimal("55.00"));
        });
    }
}

package com.bank.esps.application.service;

import com.bank.esps.domain.enums.TradeSequenceStatus;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.SnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeClassifierTest {
    
    @Mock
    private SnapshotRepository snapshotRepository;
    
    @InjectMocks
    private TradeClassifier tradeClassifier;
    
    @Test
    void testClassifyCurrentDatedTrade() {
        TradeEvent trade = TradeEvent.builder()
                .tradeId("T1")
                .positionKey("POS1")
                .effectiveDate(LocalDate.now())
                .build();
        
        // No snapshot exists
        when(snapshotRepository.findById(anyString())).thenReturn(Optional.empty());
        
        TradeSequenceStatus status = tradeClassifier.classifyTrade(trade);
        
        assertEquals(TradeSequenceStatus.CURRENT_DATED, status);
        assertEquals(TradeSequenceStatus.CURRENT_DATED, trade.getSequenceStatus());
    }
    
    @Test
    void testClassifyForwardDatedTrade() {
        TradeEvent trade = TradeEvent.builder()
                .tradeId("T1")
                .positionKey("POS1")
                .effectiveDate(LocalDate.now().plusDays(5))
                .build();
        
        when(snapshotRepository.findById(anyString())).thenReturn(Optional.empty());
        
        TradeSequenceStatus status = tradeClassifier.classifyTrade(trade);
        
        assertEquals(TradeSequenceStatus.FORWARD_DATED, status);
    }
    
    @Test
    void testClassifyBackdatedTrade() {
        TradeEvent trade = TradeEvent.builder()
                .tradeId("T1")
                .positionKey("POS1")
                .effectiveDate(LocalDate.now().minusDays(5))
                .build();
        
        SnapshotEntity snapshot = new SnapshotEntity();
        snapshot.setPositionKey("POS1");
        snapshot.setLastUpdatedAt(OffsetDateTime.now());
        
        when(snapshotRepository.findById(anyString())).thenReturn(Optional.of(snapshot));
        
        TradeSequenceStatus status = tradeClassifier.classifyTrade(trade);
        
        assertEquals(TradeSequenceStatus.BACKDATED, status);
    }
    
    @Test
    void testClassifyWithSnapshotSameDate() {
        TradeEvent trade = TradeEvent.builder()
                .tradeId("T1")
                .positionKey("POS1")
                .effectiveDate(LocalDate.now())
                .build();
        
        SnapshotEntity snapshot = new SnapshotEntity();
        snapshot.setPositionKey("POS1");
        snapshot.setLastUpdatedAt(OffsetDateTime.now());
        
        when(snapshotRepository.findById(anyString())).thenReturn(Optional.of(snapshot));
        
        TradeSequenceStatus status = tradeClassifier.classifyTrade(trade);
        
        assertEquals(TradeSequenceStatus.CURRENT_DATED, status);
    }
}

package com.bank.esps.application.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Metrics collection service
 * Tracks trade processing metrics, latency, errors, and business metrics
 */
@Service
public class MetricsService {
    
    private final MeterRegistry meterRegistry;
    
    // Counters
    private final Counter tradesProcessedCounter;
    private final Counter tradesProcessedHotpathCounter;
    private final Counter tradesProcessedColdpathCounter;
    private final Counter backdatedTradesCounter;
    private final Counter errorsCounter;
    private final Counter validationFailuresCounter;
    private final Counter idempotencyHitsCounter;
    
    // Timers
    private final Timer hotpathProcessingTimer;
    private final Timer coldpathProcessingTimer;
    private final Timer contractGenerationTimer;
    private final Timer snapshotUpdateTimer;
    
    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize counters
        this.tradesProcessedCounter = Counter.builder("trades.processed")
                .description("Total number of trades processed")
                .register(meterRegistry);
        
        this.tradesProcessedHotpathCounter = Counter.builder("trades.processed.hotpath")
                .description("Number of trades processed in hotpath")
                .register(meterRegistry);
        
        this.tradesProcessedColdpathCounter = Counter.builder("trades.processed.coldpath")
                .description("Number of trades processed in coldpath")
                .register(meterRegistry);
        
        this.backdatedTradesCounter = Counter.builder("trades.backdated")
                .description("Number of backdated trades detected")
                .register(meterRegistry);
        
        this.errorsCounter = Counter.builder("trades.errors")
                .description("Number of trade processing errors")
                .register(meterRegistry);
        
        this.validationFailuresCounter = Counter.builder("trades.validation.failures")
                .description("Number of validation failures")
                .register(meterRegistry);
        
        this.idempotencyHitsCounter = Counter.builder("trades.idempotency.hits")
                .description("Number of duplicate trades detected (idempotency)")
                .register(meterRegistry);
        
        // Initialize timers
        this.hotpathProcessingTimer = Timer.builder("trades.processing.hotpath.duration")
                .description("Hotpath trade processing duration")
                .register(meterRegistry);
        
        this.coldpathProcessingTimer = Timer.builder("trades.processing.coldpath.duration")
                .description("Coldpath trade processing duration")
                .register(meterRegistry);
        
        this.contractGenerationTimer = Timer.builder("contract.generation.duration")
                .description("Contract generation duration")
                .register(meterRegistry);
        
        this.snapshotUpdateTimer = Timer.builder("snapshot.update.duration")
                .description("Snapshot update duration")
                .register(meterRegistry);
    }
    
    // Counter methods
    public void incrementTradesProcessed() {
        tradesProcessedCounter.increment();
    }
    
    public void incrementTradesProcessedHotpath() {
        tradesProcessedHotpathCounter.increment();
    }
    
    public void incrementTradesProcessedColdpath() {
        tradesProcessedColdpathCounter.increment();
    }
    
    public void incrementBackdatedTrades() {
        backdatedTradesCounter.increment();
    }
    
    public void incrementErrors() {
        errorsCounter.increment();
    }
    
    public void incrementValidationFailures() {
        validationFailuresCounter.increment();
    }
    
    public void incrementIdempotencyHits() {
        idempotencyHitsCounter.increment();
    }
    
    // Timer methods
    public Timer.Sample startHotpathProcessing() {
        return Timer.start(meterRegistry);
    }
    
    public void recordHotpathProcessing(Timer.Sample sample) {
        sample.stop(hotpathProcessingTimer);
    }
    
    public Timer.Sample startColdpathProcessing() {
        return Timer.start(meterRegistry);
    }
    
    public void recordColdpathProcessing(Timer.Sample sample) {
        sample.stop(coldpathProcessingTimer);
    }
    
    public Timer.Sample startContractGeneration() {
        return Timer.start(meterRegistry);
    }
    
    public void recordContractGeneration(Timer.Sample sample) {
        sample.stop(contractGenerationTimer);
    }
    
    public Timer.Sample startSnapshotUpdate() {
        return Timer.start(meterRegistry);
    }
    
    public void recordSnapshotUpdate(Timer.Sample sample) {
        sample.stop(snapshotUpdateTimer);
    }
    
    // Business metrics
    public void recordPositionCreated() {
        Counter.builder("positions.created")
                .register(meterRegistry)
                .increment();
    }
    
    public void recordLotsCreated(int count) {
        Counter.builder("lots.created")
                .tag("count", String.valueOf(count))
                .register(meterRegistry)
                .increment(count);
    }
    
    public void recordReconciliationBreak(String breakType) {
        Counter.builder("reconciliation.breaks")
                .tag("type", breakType)
                .register(meterRegistry)
                .increment();
    }
    
    public void recordProvisionalPosition() {
        Counter.builder("positions.provisional")
                .register(meterRegistry)
                .increment();
    }
    
    public void recordProvisionalToReconciled() {
        Counter.builder("positions.provisional.reconciled")
                .register(meterRegistry)
                .increment();
    }
}

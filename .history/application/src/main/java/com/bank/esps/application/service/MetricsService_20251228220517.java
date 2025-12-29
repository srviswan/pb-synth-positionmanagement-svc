package com.bank.esps.application.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service for recording application metrics
 */
@Service
public class MetricsService {
    
    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);
    
    private final MeterRegistry meterRegistry;
    
    // Counters
    private final Counter tradesProcessedCounter;
    private final Counter tradesValidatedCounter;
    private final Counter tradesRejectedCounter;
    private final Counter hotpathTradesCounter;
    private final Counter coldpathTradesCounter;
    private final Counter provisionalPositionsCounter;
    private final Counter correctionsCounter;
    
    // Timers
    private final Timer hotpathProcessingTimer;
    private final Timer coldpathProcessingTimer;
    private final Timer validationTimer;
    private final Timer taxLotCalculationTimer;
    
    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize counters
        this.tradesProcessedCounter = Counter.builder("trades.processed")
                .description("Total number of trades processed")
                .register(meterRegistry);
        
        this.tradesValidatedCounter = Counter.builder("trades.validated")
                .description("Total number of trades validated")
                .register(meterRegistry);
        
        this.tradesRejectedCounter = Counter.builder("trades.rejected")
                .description("Total number of trades rejected")
                .tag("reason", "validation")
                .register(meterRegistry);
        
        this.hotpathTradesCounter = Counter.builder("trades.hotpath")
                .description("Total number of hotpath trades")
                .register(meterRegistry);
        
        this.coldpathTradesCounter = Counter.builder("trades.coldpath")
                .description("Total number of coldpath trades")
                .register(meterRegistry);
        
        this.provisionalPositionsCounter = Counter.builder("positions.provisional")
                .description("Total number of provisional positions created")
                .register(meterRegistry);
        
        this.correctionsCounter = Counter.builder("positions.corrected")
                .description("Total number of position corrections")
                .register(meterRegistry);
        
        // Initialize timers
        this.hotpathProcessingTimer = Timer.builder("trades.hotpath.processing.time")
                .description("Hotpath trade processing time")
                .register(meterRegistry);
        
        this.coldpathProcessingTimer = Timer.builder("trades.coldpath.processing.time")
                .description("Coldpath trade processing time")
                .register(meterRegistry);
        
        this.validationTimer = Timer.builder("trades.validation.time")
                .description("Trade validation time")
                .register(meterRegistry);
        
        this.taxLotCalculationTimer = Timer.builder("taxlot.calculation.time")
                .description("Tax lot calculation time")
                .register(meterRegistry);
    }
    
    public void recordTradeProcessed() {
        tradesProcessedCounter.increment();
    }
    
    public void recordTradeValidated() {
        tradesValidatedCounter.increment();
    }
    
    public void recordTradeRejected(String reason) {
        meterRegistry.counter("trades.rejected", "reason", reason).increment();
    }
    
    public void recordHotpathTrade() {
        hotpathTradesCounter.increment();
    }
    
    public void recordColdpathTrade() {
        coldpathTradesCounter.increment();
    }
    
    public void recordProvisionalPosition() {
        provisionalPositionsCounter.increment();
    }
    
    public void recordCorrection() {
        correctionsCounter.increment();
    }
    
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
    
    public Timer.Sample startValidation() {
        return Timer.start(meterRegistry);
    }
    
    public void recordValidation(Timer.Sample sample) {
        sample.stop(validationTimer);
    }
    
    public Timer.Sample startTaxLotCalculation() {
        return Timer.start(meterRegistry);
    }
    
    public void recordTaxLotCalculation(Timer.Sample sample) {
        sample.stop(taxLotCalculationTimer);
    }
    
    public void recordGauge(String name, double value, String... tags) {
        meterRegistry.gauge(name, value);
    }
}

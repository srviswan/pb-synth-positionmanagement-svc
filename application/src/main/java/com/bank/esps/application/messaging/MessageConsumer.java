package com.bank.esps.application.messaging;

import com.bank.esps.domain.event.TradeEvent;

import java.util.function.Consumer;

/**
 * Abstraction for message consumption
 * Allows switching between messaging systems (Kafka, Solace, RabbitMQ, etc.)
 * without changing application code
 */
public interface MessageConsumer {
    
    /**
     * Register a consumer for backdated trades
     * @param processor The function to process backdated trades
     */
    void subscribeToBackdatedTrades(Consumer<TradeEvent> processor);
    
    /**
     * Start consuming messages (if not auto-started)
     */
    void start();
    
    /**
     * Stop consuming messages
     */
    void stop();
    
    /**
     * Check if consumer is running
     * @return true if consumer is active
     */
    boolean isRunning();
}

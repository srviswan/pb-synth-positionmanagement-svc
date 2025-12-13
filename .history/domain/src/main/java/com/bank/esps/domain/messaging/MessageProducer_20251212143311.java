package com.bank.esps.domain.messaging;

import com.bank.esps.domain.event.TradeEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Abstraction for message publishing
 * Allows switching between messaging systems (Kafka, Solace, RabbitMQ, etc.)
 * without changing application code
 */
public interface MessageProducer {
    
    /**
     * Publish a backdated trade to the coldpath
     * @param tradeEvent The backdated trade event
     * @return CompletableFuture that completes when message is sent
     */
    CompletableFuture<Void> publishBackdatedTrade(TradeEvent tradeEvent);
    
    /**
     * Publish a trade to the Dead Letter Queue
     * @param tradeEvent The trade event that failed validation/processing
     * @param error The error message describing why it was sent to DLQ
     * @return CompletableFuture that completes when message is sent
     */
    CompletableFuture<Void> publishToDLQ(TradeEvent tradeEvent, String error);
    
    /**
     * Publish a trade to the error queue (for retryable errors)
     * @param tradeEvent The trade event that encountered a retryable error
     * @param error The error message
     * @return CompletableFuture that completes when message is sent
     */
    CompletableFuture<Void> publishToErrorQueue(TradeEvent tradeEvent, String error);
    
    /**
     * Publish a correction event (from coldpath recalculation)
     * @param positionKey The position key
     * @param correctionEvent The correction event payload (JSON string)
     * @return CompletableFuture that completes when message is sent
     */
    CompletableFuture<Void> publishCorrectionEvent(String positionKey, String correctionEvent);
}

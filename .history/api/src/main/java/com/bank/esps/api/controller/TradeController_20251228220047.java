package com.bank.esps.api.controller;

import com.bank.esps.application.service.CorrelationIdService;
import com.bank.esps.application.service.DeadLetterQueueService;
import com.bank.esps.application.service.PositionService;
import com.bank.esps.application.service.TradeValidationService;
import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.model.PositionState;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for trade operations
 */
@RestController
@RequestMapping("/api/trades")
public class TradeController {
    
    private static final Logger log = LoggerFactory.getLogger(TradeController.class);
    
    private final PositionService positionService;
    private final CorrelationIdService correlationIdService;
    private final TradeValidationService validationService;
    private final DeadLetterQueueService dlqService;
    
    public TradeController(PositionService positionService, 
                          CorrelationIdService correlationIdService,
                          TradeValidationService validationService,
                          DeadLetterQueueService dlqService) {
        this.positionService = positionService;
        this.correlationIdService = correlationIdService;
        this.validationService = validationService;
        this.dlqService = dlqService;
    }
    
    @PostMapping
    public ResponseEntity<PositionState> processTrade(@RequestBody TradeEvent tradeEvent,
                                                      HttpServletRequest request) {
        try {
            // Extract correlation ID from header if present
            String correlationId = request.getHeader("X-Correlation-ID");
            if (correlationId != null && !correlationId.isEmpty()) {
                correlationIdService.setCorrelationId(correlationId);
            } else {
                correlationIdService.generateCorrelationId();
            }
            
            log.info("Processing trade: tradeId={}, correlationId={}", 
                    tradeEvent.getTradeId(), correlationIdService.getCurrentCorrelationId());
            
            // Early validation - before processing
            java.util.List<String> validationErrors = validationService.validate(tradeEvent);
            if (!validationErrors.isEmpty()) {
                log.warn("Trade validation failed: tradeId={}, errors={}", 
                        tradeEvent.getTradeId(), validationErrors);
                // Route to DLQ
                dlqService.routeToDLQ(tradeEvent, validationErrors, "VALIDATION_FAILED");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }
            
            PositionState result = positionService.processTrade(tradeEvent);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            log.error("Validation error processing trade: {}", e.getMessage());
            dlqService.routeToDLQ(tradeEvent, java.util.List.of(e.getMessage()), "VALIDATION_ERROR");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception e) {
            log.error("Error processing trade: {}", tradeEvent.getTradeId(), e);
            // Route recoverable errors to error queue, others to DLQ
            boolean retryable = isRetryableError(e);
            if (retryable) {
                dlqService.routeToErrorQueue(tradeEvent, e.getMessage(), true);
            } else {
                dlqService.routeToDLQ(tradeEvent, java.util.List.of(e.getMessage()), "PROCESSING_ERROR");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            correlationIdService.clear();
        }
    }
    
    private boolean isRetryableError(Exception e) {
        // Determine if error is retryable (e.g., database timeout, network issues)
        return e instanceof org.springframework.dao.QueryTimeoutException ||
               e instanceof java.net.SocketTimeoutException ||
               e instanceof org.springframework.transaction.CannotCreateTransactionException;
    }
}

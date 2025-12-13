package com.bank.esps.application.service;

import com.bank.esps.domain.event.TradeEvent;
import com.bank.esps.domain.messaging.MessageProducer;
import com.bank.esps.infrastructure.persistence.entity.RegulatorySubmissionEntity;
import com.bank.esps.infrastructure.persistence.entity.SnapshotEntity;
import com.bank.esps.infrastructure.persistence.repository.RegulatorySubmissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Service for handling regulatory submissions
 * Submits trades to regulatory authorities and tracks submission status
 */
@Service
public class RegulatorySubmissionService {
    
    private static final Logger log = LoggerFactory.getLogger(RegulatorySubmissionService.class);
    
    private final RegulatorySubmissionRepository regulatorySubmissionRepository;
    private final MessageProducer messageProducer;
    private final ObjectMapper objectMapper;
    
    public RegulatorySubmissionService(
            RegulatorySubmissionRepository regulatorySubmissionRepository,
            MessageProducer messageProducer,
            ObjectMapper objectMapper) {
        this.regulatorySubmissionRepository = regulatorySubmissionRepository;
        this.messageProducer = messageProducer;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Submit a trade to regulatory authorities (hotpath)
     * Called after a trade is successfully processed
     */
    @Transactional
    public void submitTradeToRegulator(TradeEvent tradeEvent, SnapshotEntity snapshot) {
        try {
            String tradeId = tradeEvent.getTradeId();
            String positionKey = tradeEvent.getPositionKey();
            String upi = snapshot.getUti();
            
            log.info("Submitting trade {} to regulator for position {} with UPI {}", 
                    tradeId, positionKey, upi);
            
            // 1. Create regulatory submission record
            RegulatorySubmissionEntity submission = RegulatorySubmissionEntity.builder()
                    .tradeId(tradeId)
                    .positionKey(positionKey)
                    .submissionType("TRADE_REPORT")
                    .status("PENDING")
                    .correlationId(tradeEvent.getCorrelationId())
                    .submittedAt(OffsetDateTime.now())
                    .retryCount(0)
                    .build();
            
            final RegulatorySubmissionEntity savedSubmission = regulatorySubmissionRepository.save(submission);
            final UUID submissionId = savedSubmission.getSubmissionId();
            log.debug("Created regulatory submission record: {}", submissionId);
            
            // 2. Create regulatory event payload
            String regulatoryEvent = createTradeReportEvent(tradeEvent, snapshot, submissionId);
            
            // 3. Publish to regulatory events topic
            messageProducer.publishRegulatoryEvent(positionKey, regulatoryEvent)
                    .thenRun(() -> {
                        // Update submission status to SUBMITTED asynchronously
                        try {
                            RegulatorySubmissionEntity submissionToUpdate = 
                                    regulatorySubmissionRepository.findById(submissionId)
                                            .orElse(null);
                            if (submissionToUpdate != null) {
                                submissionToUpdate.setStatus("SUBMITTED");
                                regulatorySubmissionRepository.save(submissionToUpdate);
                                log.info("Regulatory submission {} marked as SUBMITTED for trade {}", 
                                        submissionId, tradeId);
                            }
                        } catch (Exception e) {
                            log.error("Error updating submission status for trade {}", tradeId, e);
                        }
                    })
                    .exceptionally(ex -> {
                        log.error("Error publishing regulatory event for trade {}", tradeId, ex);
                        try {
                            RegulatorySubmissionEntity submissionToUpdate = 
                                    regulatorySubmissionRepository.findById(submissionId)
                                            .orElse(null);
                            if (submissionToUpdate != null) {
                                submissionToUpdate.setStatus("FAILED");
                                submissionToUpdate.setErrorMessage(ex.getMessage());
                                regulatorySubmissionRepository.save(submissionToUpdate);
                            }
                        } catch (Exception e) {
                            log.error("Error updating submission status to FAILED for trade {}", tradeId, e);
                        }
                        return null;
                    });
            
            log.info("✅ Regulatory submission initiated for trade {} (submission ID: {})", 
                    tradeId, submissionId);
            
        } catch (Exception e) {
            log.error("Error submitting trade {} to regulator", tradeEvent.getTradeId(), e);
            // Don't throw - regulatory submission failure shouldn't block trade processing
        }
    }
    
    /**
     * Create regulatory event payload for trade report
     */
    private String createTradeReportEvent(TradeEvent tradeEvent, SnapshotEntity snapshot, UUID submissionId) {
        try {
            // Create comprehensive regulatory event
            String regulatoryEvent = String.format(
                    "{\"type\":\"TRADE_REPORT\"," +
                    "\"submissionId\":\"%s\"," +
                    "\"tradeId\":\"%s\"," +
                    "\"positionKey\":\"%s\"," +
                    "\"upi\":\"%s\"," +
                    "\"tradeType\":\"%s\"," +
                    "\"quantity\":%s," +
                    "\"price\":%s," +
                    "\"effectiveDate\":\"%s\"," +
                    "\"contractId\":\"%s\"," +
                    "\"correlationId\":\"%s\"," +
                    "\"causationId\":\"%s\"," +
                    "\"userId\":\"%s\"," +
                    "\"positionStatus\":\"%s\"," +
                    "\"positionVersion\":%d," +
                    "\"submittedAt\":\"%s\"," +
                    "\"actionRequired\":\"ACKNOWLEDGE_TRADE_REPORT\"}",
                    submissionId,
                    tradeEvent.getTradeId(),
                    tradeEvent.getPositionKey(),
                    snapshot.getUti() != null ? snapshot.getUti() : "",
                    tradeEvent.getTradeType(),
                    tradeEvent.getQuantity(),
                    tradeEvent.getPrice(),
                    tradeEvent.getEffectiveDate(),
                    tradeEvent.getContractId() != null ? tradeEvent.getContractId() : "",
                    tradeEvent.getCorrelationId() != null ? tradeEvent.getCorrelationId() : "",
                    tradeEvent.getCausationId() != null ? tradeEvent.getCausationId() : "",
                    tradeEvent.getUserId() != null ? tradeEvent.getUserId() : "",
                    snapshot.getStatus() != null ? snapshot.getStatus().name() : "UNKNOWN",
                    snapshot.getLastVer() != null ? snapshot.getLastVer() : 0L,
                    OffsetDateTime.now());
            
            return regulatoryEvent;
            
        } catch (Exception e) {
            log.error("Error creating regulatory event payload for trade {}", tradeEvent.getTradeId(), e);
            throw new RuntimeException("Failed to create regulatory event", e);
        }
    }
    
    /**
     * Update regulatory submission status (called by regulatory response handler)
     */
    @Transactional
    public void updateSubmissionStatus(UUID submissionId, String status, String responsePayload, String errorMessage) {
        try {
            RegulatorySubmissionEntity submission = regulatorySubmissionRepository.findById(submissionId)
                    .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
            
            submission.setStatus(status);
            submission.setResponseReceivedAt(OffsetDateTime.now());
            if (responsePayload != null) {
                submission.setResponsePayload(responsePayload);
            }
            if (errorMessage != null) {
                submission.setErrorMessage(errorMessage);
            }
            
            regulatorySubmissionRepository.save(submission);
            log.info("Updated regulatory submission {} status to {}", submissionId, status);
            
        } catch (Exception e) {
            log.error("Error updating regulatory submission {} status", submissionId, e);
        }
    }
    
    /**
     * Retry failed regulatory submission
     */
    @Transactional
    public void retrySubmission(UUID submissionId) {
        try {
            RegulatorySubmissionEntity submission = regulatorySubmissionRepository.findById(submissionId)
                    .orElseThrow(() -> new IllegalArgumentException("Submission not found: " + submissionId));
            
            submission.setStatus("PENDING");
            submission.setRetryCount(submission.getRetryCount() + 1);
            submission.setErrorMessage(null);
            
            regulatorySubmissionRepository.save(submission);
            log.info("Retrying regulatory submission {} (attempt {})", submissionId, submission.getRetryCount());
            
        } catch (Exception e) {
            log.error("Error retrying regulatory submission {}", submissionId, e);
        }
    }
}

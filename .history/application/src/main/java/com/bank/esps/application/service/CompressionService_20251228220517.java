package com.bank.esps.application.service;

import com.bank.esps.domain.model.CompressedLots;
import com.bank.esps.domain.model.PositionState;
import com.bank.esps.domain.model.TaxLot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for compressing and decompressing position snapshots
 */
@Service
public class CompressionService {
    
    private static final Logger log = LoggerFactory.getLogger(CompressionService.class);
    
    private final ObjectMapper objectMapper;
    private static final int COMPRESSION_THRESHOLD = 10; // Compress if more than 10 lots
    
    public CompressionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Compress position state if lot count exceeds threshold
     */
    public String compressPositionState(PositionState state) {
        try {
            if (shouldCompress(state)) {
                // Create compressed version
                CompressedLots compressedLots = CompressedLots.compress(state.getOpenLots());
                
                // Create compressed position state
                PositionState compressed = PositionState.builder()
                        .positionKey(state.getPositionKey())
                        .account(state.getAccount())
                        .instrument(state.getInstrument())
                        .currency(state.getCurrency())
                        .version(state.getVersion())
                        .build();
                
                // Store compressed lots in a custom JSON structure
                java.util.Map<String, Object> compressedData = new java.util.HashMap<>();
                compressedData.put("positionKey", state.getPositionKey());
                compressedData.put("account", state.getAccount());
                compressedData.put("instrument", state.getInstrument());
                compressedData.put("currency", state.getCurrency());
                compressedData.put("version", state.getVersion());
                compressedData.put("compressedLots", compressedLots);
                compressedData.put("compressed", true);
                
                String json = objectMapper.writeValueAsString(compressedData);
                log.debug("Compressed position state: {} lots -> compressed format", 
                        state.getOpenLots() != null ? state.getOpenLots().size() : 0);
                return json;
            } else {
                // Return normal JSON for small positions
                return objectMapper.writeValueAsString(state);
            }
        } catch (Exception e) {
            log.error("Failed to compress position state", e);
            // Fallback to normal serialization
            try {
                return objectMapper.writeValueAsString(state);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to serialize position state", ex);
            }
        }
    }
    
    /**
     * Decompress position state from JSON
     */
    public PositionState decompressPositionState(String json) {
        try {
            // Try to parse as compressed format first
            java.util.Map<String, Object> data = objectMapper.readValue(json, 
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
            
            Boolean compressed = (Boolean) data.get("compressed");
            if (Boolean.TRUE.equals(compressed)) {
                // Decompress
                CompressedLots compressedLots = objectMapper.convertValue(data.get("compressedLots"), 
                        CompressedLots.class);
                List<TaxLot> lots = compressedLots.inflate();
                
                PositionState state = PositionState.builder()
                        .positionKey((String) data.get("positionKey"))
                        .account((String) data.get("account"))
                        .instrument((String) data.get("instrument"))
                        .currency((String) data.get("currency"))
                        .version(((Number) data.get("version")).intValue())
                        .openLots(lots)
                        .build();
                
                log.debug("Decompressed position state: {} lots", lots.size());
                return state;
            } else {
                // Normal format
                return objectMapper.readValue(json, PositionState.class);
            }
        } catch (Exception e) {
            log.error("Failed to decompress position state, trying normal format", e);
            try {
                return objectMapper.readValue(json, PositionState.class);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to deserialize position state", ex);
            }
        }
    }
    
    /**
     * Determine if position should be compressed
     */
    private boolean shouldCompress(PositionState state) {
        if (state.getOpenLots() == null) {
            return false;
        }
        return state.getOpenLots().size() > COMPRESSION_THRESHOLD;
    }
}

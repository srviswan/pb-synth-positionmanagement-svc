// FILE: src/main/java/com/bank/esps/service/PositionService.java

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class PositionService {

    private final EventStoreRepository eventStore;
    private final SnapshotRepository snapshotRepo;
    private final LotLogic taxLotEngine; // Handles FIFO/LIFO logic

    /**
     * Main Ingestion Point.
     * @param trade The incoming CDM-style trade object
     */
    @Transactional
    public void processTrade(TradeEvent trade) {
        String posKey = trade.getPositionKey();
        
        // 1. Load State (Optimistic Locking Strategy)
        Snapshot snap = snapshotRepo.findById(posKey)
            .orElse(Snapshot.createNew(posKey));
        
        long expectedVer = snap.getLastVer() + 1;

        // 2. Apply Business Logic
        PositionState state = snap.inflate(); // Decompress lots
        LotAllocationResult result;
        
        if (trade.isIncrease()) {
            result = taxLotEngine.addLot(state, trade);
        } else {
            // "Fat Snapshot" optimization handles large lot counts here
            result = taxLotEngine.reduceLots(state, trade, snap.getContractRules());
        }

        // 3. Persist Event (The Source of Truth)
        EventEntity event = new EventEntity();
        event.setPositionKey(posKey);
        event.setEventVer(expectedVer);
        event.setType(trade.getType());
        event.setPayload(toJson(trade));
        event.setMetaLots(toJson(result.getAllocations())); // Audit Trail
        
        try {
            eventStore.save(event);
        } catch (DuplicateKeyException e) {
            // Concurrency detected: Another thread updated this position.
            // Action: Retry the whole method.
            throw new RetryableException("Optimistic Lock Failure");
        }

        // 4. Update Snapshot (The Cache)
        snap.setLastVer(expectedVer);
        snap.setTaxLotsCompressed(compressLots(state.getOpenLots()));
        snapshotRepo.save(snap);
    }
}
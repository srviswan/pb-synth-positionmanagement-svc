sequenceDiagram
    participant K as Kafka Trade Events
    participant TPS as Trade Processing Service
    participant Classifier as Trade Classifier
    participant CGS as Contract Generation
    participant SNAP as Snapshot DB
    participant EVT as Event DB
    participant ColdK as Kafka Backdated Trades
    
    K->>TPS: 1. Trade(Buy 100, Date: 2024-01-15)
    TPS->>Classifier: 2. Classify Trade
    TPS->>SNAP: 3. GetLatestSnapshot(Key)
    SNAP-->>TPS: {Ver: 10, Qty: 500, Date: 2024-01-14}
    
    Classifier->>Classifier: 4. Compare Dates
    
    alt Current/Forward-Dated Trade
        Classifier->>TPS: Route to Hotpath
        TPS->>TPS: 5. Calc New State (Qty: 600)
        TPS->>CGS: 6. Generate Contract (Synchronous)
        CGS-->>TPS: Contract ID + USI
        TPS->>EVT: 7. INSERT Event (Ver: 11)
        alt Success
            EVT-->>TPS: OK
            TPS->>SNAP: 8. Upsert Snapshot (Ver: 11, RECONCILED)
        else Version Conflict
            EVT-->>TPS: Error (Duplicate Key)
            TPS->>TPS: 9. Retry from Step 3
        end
    else Backdated Trade
        Classifier->>TPS: Route to Coldpath
        TPS->>ColdK: 10. Publish to Backdated Trades Topic
        TPS->>TPS: 11. Calc Provisional State (Qty: ~600)
        TPS->>SNAP: 12. Upsert Provisional Snapshot (PROVISIONAL)
        Note over TPS,SNAP: Hotpath continues, no blocking
    end
    
    Note over ColdK: Coldpath processes asynchronously
    ColdK->>ColdK: 13. Recalculation Service consumes
    ColdK->>EVT: 14. Load Event Stream
    EVT-->>ColdK: All events for position
    ColdK->>ColdK: 15. Inject backdated trade at correct position
    ColdK->>ColdK: 16. Replay all events chronologically
    ColdK->>EVT: 17. INSERT Corrected Events
    ColdK->>SNAP: 18. Override Provisional → RECONCILED
    ColdK->>K: 19. Publish Correction Event
graph TB
    subgraph "HOTPATH - Real-Time Processing"
        Consumer[Kafka Consumer\nTrade Events Topic]
        
        Classifier{Trade Classifier}
        Consumer --> Classifier
        
        Classifier -->|Current/Forward-Dated| HotIngest[Hotpath Ingestion\nHandler]
        Classifier -->|Backdated| ColdRouter[Route to\nColdpath Topic]
        
        Contract[Contract Generation\nService - Synchronous]
        HotIngest --> Contract
        
        Reporting[USI/Regulatory\nReporting]
        Contract --> Reporting
        
        HotState[State Manager\nTax Lot Logic]
        HotIngest --> HotState
        
        HotDAO[Persistence Layer]
        HotState --> HotDAO
        
        Provisional[Provisional Position\nManager]
        ColdRouter --> Provisional
        Provisional --> HotDAO
    end
    
    subgraph "COLDPATH - Async Recalculation"
        ColdConsumer[Kafka Consumer\nBackdated Trades Topic]
        
        Recalc[Recalculation Service]
        ColdConsumer --> Recalc
        
        EventLoader[Event Stream Loader]
        Recalc --> EventLoader
        
        Replay[Event Replay Engine\nChronological Order]
        EventLoader --> Replay
        
        TaxLotRecalc[Tax Lot Recalculation\nFIFO/LIFO/HIFO]
        Replay --> TaxLotRecalc
        
        Correction[Correction Generator]
        TaxLotRecalc --> Correction
        
        ColdDAO[Persistence Layer]
        Correction --> ColdDAO
        
        Notify[Correction Event\nPublisher]
        Correction --> Notify
    end
    
    subgraph "Shared Persistence"
        PG_Event[(Event Store\nPostgres Partitioned)]
        PG_Snap[(Snapshot Store\nRedis/Postgres)]
        
        HotDAO --> PG_Event
        HotDAO --> PG_Snap
        ColdDAO --> PG_Event
        ColdDAO --> PG_Snap
    end
graph TD
    subgraph "Upstream (Sources)"
        TC[Trade Capture System] -->|Trades| Kafka_Ingest
        MKT[Market Data Feed] -->|Prices/Rates| Kafka_Mkt
        REF[Contract Service] -->|Terms/Rules| Kafka_Ref
    end

    subgraph "Core Domain (ESPS) - Hotpath"
        Kafka_Ingest[Kafka: Trade Events\nCurrent/Forward-Dated]
        Kafka_Mkt[Kafka: Market Data]
        Kafka_Ref[Kafka: Contract Events]
        
        Hotpath_Svc[Hotpath Service\nReal-Time Processing]
        
        Kafka_Ingest --> Hotpath_Svc
        Kafka_Mkt --> Hotpath_Svc
        Kafka_Ref --> Hotpath_Svc
        
        Hotpath_Svc -->|Backdated Trades| Kafka_Backdated[Kafka: Backdated\nTrades Topic]
    end
    
    subgraph "Core Domain (ESPS) - Coldpath"
        Coldpath_Svc[Coldpath Service\nAsync Recalculation]
        
        Kafka_Backdated --> Coldpath_Svc
    end

    subgraph "Persistence"
        PG_Event[(Event Store\nPostgres Partitioned)]
        PG_Snap[(Snapshot Store\nRedis/Postgres)]
        S3_Cold[(Cold Store\nS3 Parquet)]
        
        Hotpath_Svc --> PG_Event
        Hotpath_Svc --> PG_Snap
        Coldpath_Svc --> PG_Event
        Coldpath_Svc --> PG_Snap
        PG_Event -.-> S3_Cold
    end

    subgraph "Downstream (Sinks)"
        CDC[Debezium CDC]
        ACL[Legacy Adaptor (ACL)]
        Risk[Risk Engine]
        Leg_Sys[Legacy System]
        
        PG_Event --> CDC
        CDC -->|Flattened Stream| Risk
        CDC -->|Raw Stream| ACL
        ACL -->|Transformed| Leg_Sys
        
        Coldpath_Svc -->|Correction Events| Risk
    end
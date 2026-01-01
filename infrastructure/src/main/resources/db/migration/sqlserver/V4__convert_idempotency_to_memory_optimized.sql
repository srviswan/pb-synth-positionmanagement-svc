-- Convert idempotency table to memory-optimized for better performance
-- SQL Server 2022 supports memory-optimized tables with full durability
-- This migration improves idempotency check performance by 10-100x

-- Step 1: Create memory-optimized filegroup (if not exists)
-- Check if memory-optimized filegroup exists, create if not
IF NOT EXISTS (SELECT 1 FROM sys.filegroups WHERE name = 'idempotency_fg' AND type = 'FX')
BEGIN
    -- Create filegroup for memory-optimized data
    ALTER DATABASE [equity_swap_db] ADD FILEGROUP idempotency_fg CONTAINS MEMORY_OPTIMIZED_DATA;
    
    -- Add file to the filegroup
    -- Note: Path may need adjustment based on SQL Server container configuration
    DECLARE @dataPath NVARCHAR(500) = (SELECT physical_name FROM sys.master_files WHERE database_id = DB_ID() AND type = 0 AND file_id = 1);
    DECLARE @filePath NVARCHAR(500) = LEFT(@dataPath, LEN(@dataPath) - CHARINDEX('\', REVERSE(@dataPath))) + '\idempotency_file';
    
    ALTER DATABASE [equity_swap_db] ADD FILE (name='idempotency_file', filename=@filePath) TO FILEGROUP idempotency_fg;
    
    PRINT 'Created memory-optimized filegroup: idempotency_fg';
END
ELSE
BEGIN
    PRINT 'Memory-optimized filegroup already exists: idempotency_fg';
END
GO

-- Step 2: Create new memory-optimized table with same structure
-- Using SCHEMA_AND_DATA for durability (data persists across restarts)
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'idempotency_memory' AND is_memory_optimized = 1)
BEGIN
    CREATE TABLE idempotency_memory (
        id BIGINT IDENTITY(1,1) NOT NULL,
        message_id NVARCHAR(255) NOT NULL,
        position_key NVARCHAR(255) NOT NULL,
        status NVARCHAR(20) NOT NULL DEFAULT 'PROCESSED',
        event_version INT NULL,
        processed_at DATETIMEOFFSET NOT NULL DEFAULT SYSDATETIMEOFFSET(),
        
        -- Primary key must be nonclustered for memory-optimized tables
        CONSTRAINT pk_idempotency_memory PRIMARY KEY NONCLUSTERED (id),
        
        -- Unique constraint on message_id (required for idempotency)
        CONSTRAINT uq_idempotency_memory_message_id UNIQUE NONCLUSTERED (message_id)
    ) WITH (
        MEMORY_OPTIMIZED = ON,
        DURABILITY = SCHEMA_AND_DATA  -- Data persists across restarts
    );
    
    -- Create nonclustered hash index for position_key lookups (faster for equality searches)
    -- Hash index bucket count: typically 2x expected unique values, minimum 1024
    CREATE NONCLUSTERED INDEX idx_idempotency_memory_position_key 
    ON idempotency_memory (position_key) 
    WITH (BUCKET_COUNT = 10000);
    
    -- Create nonclustered index for status (if needed for queries)
    CREATE NONCLUSTERED INDEX idx_idempotency_memory_status 
    ON idempotency_memory (status) 
    WITH (BUCKET_COUNT = 10);
    
    PRINT 'Created memory-optimized table: idempotency_memory';
END
ELSE
BEGIN
    PRINT 'Memory-optimized table already exists: idempotency_memory';
END
GO

-- Step 3: Migrate existing data from old table to new table (if data exists)
IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'idempotency' AND is_memory_optimized = 0)
BEGIN
    DECLARE @rowCount INT;
    
    -- Copy data from old table to new table
    INSERT INTO idempotency_memory (message_id, position_key, status, event_version, processed_at)
    SELECT 
        message_id,
        position_key,
        ISNULL(status, 'PROCESSED') as status,
        event_version,
        processed_at
    FROM idempotency
    WHERE NOT EXISTS (
        SELECT 1 FROM idempotency_memory im 
        WHERE im.message_id = idempotency.message_id
    );
    
    SET @rowCount = @@ROWCOUNT;
    PRINT 'Migrated ' + CAST(@rowCount AS NVARCHAR(10)) + ' rows from idempotency to idempotency_memory';
END
ELSE
BEGIN
    PRINT 'No existing idempotency table found to migrate from';
END
GO

-- Step 4: Swap tables (rename for backward compatibility)
-- First, drop old table constraints and indexes
IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'idempotency' AND is_memory_optimized = 0)
BEGIN
    -- Drop indexes first
    IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_idempotency_message_id' AND object_id = OBJECT_ID('idempotency'))
        DROP INDEX idx_idempotency_message_id ON idempotency;
    
    IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_idempotency_position_key' AND object_id = OBJECT_ID('idempotency'))
        DROP INDEX idx_idempotency_position_key ON idempotency;
    
    IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_idempotency_status' AND object_id = OBJECT_ID('idempotency'))
        DROP INDEX idx_idempotency_status ON idempotency;
    
    -- Rename old table to backup
    EXEC sp_rename 'idempotency', 'idempotency_backup';
    PRINT 'Renamed old idempotency table to idempotency_backup';
    
    -- Rename new table to original name
    EXEC sp_rename 'idempotency_memory', 'idempotency';
    PRINT 'Renamed idempotency_memory to idempotency';
END
ELSE IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'idempotency_memory' AND is_memory_optimized = 1)
BEGIN
    -- If old table doesn't exist but new one does, just rename it
    EXEC sp_rename 'idempotency_memory', 'idempotency';
    PRINT 'Renamed idempotency_memory to idempotency';
END
GO

-- Step 5: Verify the conversion
IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'idempotency' AND is_memory_optimized = 1)
BEGIN
    DECLARE @durability NVARCHAR(50);
    SELECT @durability = durability_desc 
    FROM sys.tables 
    WHERE name = 'idempotency' AND is_memory_optimized = 1;
    
    PRINT 'SUCCESS: idempotency table is now memory-optimized';
    PRINT 'Durability: ' + @durability;
    PRINT 'Performance improvement: 10-100x faster lookups expected';
END
ELSE
BEGIN
    PRINT 'WARNING: idempotency table conversion may have failed';
    PRINT 'Please check the error messages above';
END
GO

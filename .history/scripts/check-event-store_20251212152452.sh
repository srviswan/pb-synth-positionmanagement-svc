#!/bin/bash

# Script to check event store data
# Usage: ./scripts/check-event-store.sh

set -e

DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-equity_swap_db}
DB_USER=${DB_USER:-postgres}
DB_PASSWORD=${DB_PASSWORD:-postgres}

echo "=========================================="
echo "Checking Event Store Data"
echo "=========================================="

# Export password for psql
export PGPASSWORD=$DB_PASSWORD

echo ""
echo "1. Total events in event_store:"
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT COUNT(*) as total_events FROM event_store;"

echo ""
echo "2. Events by type:"
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT event_type, COUNT(*) as count FROM event_store GROUP BY event_type ORDER BY count DESC;"

echo ""
echo "3. Latest 10 events:"
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT position_key, event_ver, event_type, effective_date, occurred_at FROM event_store ORDER BY occurred_at DESC LIMIT 10;"

echo ""
echo "4. Events by position (top 10):"
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT position_key, COUNT(*) as event_count, MAX(event_ver) as max_version FROM event_store GROUP BY position_key ORDER BY event_count DESC LIMIT 10;"

echo ""
echo "5. Check partitions:"
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT schemaname, tablename, n_live_tup as row_count FROM pg_stat_user_tables WHERE tablename LIKE 'event_store%' ORDER BY tablename;"

echo ""
echo "=========================================="
echo "Done"
echo "=========================================="

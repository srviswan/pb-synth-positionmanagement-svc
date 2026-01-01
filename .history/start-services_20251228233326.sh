#!/bin/bash

# Script to start required services for Position Management Service
# This script starts SQL Server, Redis, Kafka, and Solace (if needed)

set -e

echo "🚀 Starting required services for Position Management Service..."
echo ""

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
    echo "❌ docker-compose is not installed. Please install Docker Desktop."
    exit 1
fi

# Check if Docker is running
if ! docker info &> /dev/null; then
    echo "❌ Docker is not running. Please start Docker Desktop."
    exit 1
fi

# Start SQL Server
echo "📦 Starting SQL Server..."
docker-compose up -d sqlserver

# Wait for SQL Server to be ready
echo "⏳ Waiting for SQL Server to be ready..."
timeout=60
elapsed=0
while [ $elapsed -lt $timeout ]; do
    if docker exec position-mgmt-sqlserver /opt/mssql-tools/bin/sqlcmd -S localhost -U SA -P 'Test@123456' -Q 'SELECT 1' &> /dev/null; then
        echo "✅ SQL Server is ready!"
        break
    fi
    sleep 2
    elapsed=$((elapsed + 2))
    echo "   Still waiting... (${elapsed}s/${timeout}s)"
done

if [ $elapsed -ge $timeout ]; then
    echo "⚠️  SQL Server may not be ready yet, but continuing..."
fi

# Start Redis
echo "📦 Starting Redis..."
docker-compose up -d redis

# Start Kafka and Zookeeper
echo "📦 Starting Kafka and Zookeeper..."
docker-compose up -d zookeeper kafka

# Check if Solace is needed
if [ "${MESSAGING_PROVIDER:-kafka}" == "solace" ]; then
    echo "📦 Starting Solace PubSub+..."
    docker-compose up -d solace
    
    echo "⏳ Waiting for Solace to be ready..."
    sleep 10
    echo "✅ Solace started (may take a minute to fully initialize)"
fi

echo ""
echo "✅ All services started!"
echo ""
echo "To check service status: docker-compose ps"
echo "To view logs: docker-compose logs -f [service-name]"
echo "To stop services: docker-compose down"

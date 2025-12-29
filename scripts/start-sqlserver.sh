#!/bin/bash

# Quick Start Script for MS SQL Server Implementation
# This script sets up and starts the Position Management Service with MS SQL Server

set -e

echo "=========================================="
echo "Position Management Service - SQL Server Setup"
echo "=========================================="

# Configuration
DB_TYPE=${DB_TYPE:-sqlserver}
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-1433}
DB_NAME=${DB_NAME:-equity_swap_db}
DB_USERNAME=${DB_USERNAME:-SA}
DB_PASSWORD=${DB_PASSWORD:-Test@123456}
CONTAINER_NAME=${CONTAINER_NAME:-position-sqlserver}

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to print colored messages
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Docker is available
check_docker() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed or not in PATH"
        print_info "Please install Docker or use a local SQL Server instance"
        exit 1
    fi
    
    if ! docker info &> /dev/null; then
        print_error "Docker daemon is not running"
        print_info "Please start Docker Desktop or Docker daemon"
        exit 1
    fi
}

# Start or create SQL Server container
setup_sqlserver() {
    print_info "Setting up MS SQL Server container..."
    
    # Check if container exists
    if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        # Container exists, check if running
        if docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
            print_info "SQL Server container '${CONTAINER_NAME}' is already running"
        else
            print_info "Starting existing SQL Server container '${CONTAINER_NAME}'..."
            docker start ${CONTAINER_NAME}
        fi
    else
        # Create new container
        print_info "Creating new SQL Server container '${CONTAINER_NAME}'..."
        docker run -e "ACCEPT_EULA=Y" \
            -e "SA_PASSWORD=${DB_PASSWORD}" \
            -p ${DB_PORT}:1433 \
            --name ${CONTAINER_NAME} \
            -d mcr.microsoft.com/mssql/server:2022-latest
        
        print_info "Waiting for SQL Server to be ready (30 seconds)..."
        sleep 30
    fi
    
    # Verify container is running
    if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        print_error "Failed to start SQL Server container"
        exit 1
    fi
    
    print_info "SQL Server container is running"
}

# Wait for SQL Server to be ready
wait_for_sqlserver() {
    print_info "Waiting for SQL Server to accept connections..."
    
    max_attempts=30
    attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if docker exec ${CONTAINER_NAME} /opt/mssql-tools/bin/sqlcmd \
            -S localhost -U ${DB_USERNAME} -P "${DB_PASSWORD}" \
            -Q "SELECT 1" &> /dev/null; then
            print_info "SQL Server is ready!"
            return 0
        fi
        
        attempt=$((attempt + 1))
        echo -n "."
        sleep 2
    done
    
    echo ""
    print_error "SQL Server did not become ready within timeout"
    return 1
}

# Set environment variables
setup_environment() {
    print_info "Setting up environment variables..."
    
    export DB_TYPE=${DB_TYPE}
    export DB_HOST=${DB_HOST}
    export DB_PORT=${DB_PORT}
    export DB_NAME=${DB_NAME}
    export DB_USERNAME=${DB_USERNAME}
    export DB_PASSWORD=${DB_PASSWORD}
    
    # Optional: Set other infrastructure variables
    export REDIS_HOST=${REDIS_HOST:-localhost}
    export REDIS_PORT=${REDIS_PORT:-6379}
    export KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}
    export MESSAGING_TYPE=${MESSAGING_TYPE:-kafka}
    export CACHE_TYPE=${CACHE_TYPE:-redis}
    export CONTRACT_SERVICE_TYPE=${CONTRACT_SERVICE_TYPE:-mock}
    
    print_info "Environment variables configured:"
    echo "  DB_TYPE=${DB_TYPE}"
    echo "  DB_HOST=${DB_HOST}"
    echo "  DB_PORT=${DB_PORT}"
    echo "  DB_NAME=${DB_NAME}"
    echo "  DB_USERNAME=${DB_USERNAME}"
}

# Build the project
build_project() {
    print_info "Building project..."
    
    if mvn clean install -DskipTests; then
        print_info "Build successful!"
    else
        print_error "Build failed!"
        exit 1
    fi
}

# Start the application
start_application() {
    print_info "Starting Position Management Service..."
    print_info "Application will be available at http://localhost:8080"
    print_info "Press Ctrl+C to stop the application"
    echo ""
    
    mvn spring-boot:run -pl api
}

# Main execution
main() {
    print_info "Starting setup process..."
    
    # Check Docker
    check_docker
    
    # Setup SQL Server
    setup_sqlserver
    
    # Wait for SQL Server
    wait_for_sqlserver
    
    # Setup environment
    setup_environment
    
    # Build project
    build_project
    
    # Start application
    start_application
}

# Run main function
main

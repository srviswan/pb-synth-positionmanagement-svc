# Solace Docker Setup

## Official Docker Image

Yes! Solace provides an official Docker image for Solace PubSub+ Event Broker:

**Image**: `solace/solace-pubsub-standard:latest`

**Docker Hub**: https://hub.docker.com/r/solace/solace-pubsub-standard

## Quick Start

### Pull the Image

```bash
docker pull solace/solace-pubsub-standard:latest
```

### Run with Docker Compose

The Solace broker has been added to `docker-compose.yml`. To start it:

```bash
# Start all services (including Solace)
docker-compose up -d

# Or start only Solace
docker-compose up -d solace
```

### Run Standalone

```bash
docker run -d \
  --name solace-broker \
  --shm-size=1g \
  -p 8080:8080 \
  -p 55555:55555 \
  -p 8008:8008 \
  -p 1883:1883 \
  -p 9000:9000 \
  -e username_admin_globalaccesslevel=admin \
  -e username_admin_password=admin \
  -e system_scaling_maxconnectioncount=100 \
  -e system_scaling_maxqueuemessagecount=1000 \
  solace/solace-pubsub-standard:latest
```

## Ports

| Port | Service | Description |
|------|---------|-------------|
| 8080 | Management UI | Solace PubSub+ Manager (Web UI) |
| 55555 | SMF | Solace Message Format (JMS, REST, etc.) |
| 8008 | Management API | REST API for management |
| 1883 | MQTT | MQTT protocol support |
| 9000 | Internal | Internal broker communication |

**Note**: Port 8080 conflicts with the application port. If you need both running:
- Use a different host port for Solace: `8081:8080`
- Or use a different host port for the app: `8081:8080` in application.yml

## Default Credentials

When using the Docker image with the default environment variables:

- **Username**: `admin`
- **Password**: `admin`
- **VPN**: `default` (default message VPN)

## Configuration for Position Management Service

Once Solace is running, configure the application:

### 1. Set Environment Variables

```bash
export MESSAGING_PROVIDER=solace
export SOLACE_HOST=localhost
export SOLACE_PORT=55555
export SOLACE_VPN=default
export SOLACE_USERNAME=admin
export SOLACE_PASSWORD=admin
export SOLACE_CLIENT_NAME=position-management-service
```

### 2. Or Update application.yml

```yaml
app:
  messaging:
    provider: solace

spring:
  solace:
    host: localhost
    port: 55555
    msgVpn: default
    clientUsername: admin
    clientPassword: admin
    clientName: position-management-service
```

## Accessing Solace Management UI

Once the container is running, access the Solace PubSub+ Manager:

**URL**: http://localhost:8081 (port 8081 to avoid conflict with application on 8080)

**Login**:
- Username: `admin`
- Password: `admin`

## Health Check

The Docker Compose configuration includes a health check. Verify Solace is healthy:

```bash
docker ps
# Check STATUS column for "healthy"

# Or check logs
docker logs solace-broker
```

## Creating Topics/Queues

Topics and queues can be created via:
1. **Solace PubSub+ Manager** (Web UI): http://localhost:8080
2. **REST API**: http://localhost:8008
3. **SEMP (Solace Element Management Protocol)**: Via REST API

### Example: Create a Queue via REST API

```bash
curl -X POST http://localhost:8008/SEMP/v2/config/msgVpns/default/queues \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "queueName": "backdated-trades",
    "accessType": "exclusive",
    "permission": "consume"
  }'
```

## Troubleshooting

### Port Conflicts

The docker-compose.yml already uses port 8081 for Solace UI to avoid conflict with the application on port 8080. If you need to change it:

```yaml
ports:
  - "8082:8080"  # Use 8082 on host instead
```

Then access Solace UI at the new port.

### Container Won't Start

Check logs:
```bash
docker logs solace-broker
```

Common issues:
- Insufficient shared memory: Ensure `shm_size: '1gb'` is set
- Port conflicts: Check if ports are already in use
- Resource constraints: Solace requires sufficient memory

### Connection Issues

Verify connectivity:
```bash
# Test SMF port
telnet localhost 55555

# Test Management API
curl http://localhost:8008/SEMP/v2/config/about/api
```

## Production Considerations

For production use:

1. **Change default passwords**: Set strong passwords via environment variables
2. **Resource limits**: Adjust `shm_size` and `ulimits` based on load
3. **Persistence**: Volumes are configured for data persistence
4. **High Availability**: Consider Solace HA deployment
5. **Security**: Enable TLS/SSL for production
6. **Monitoring**: Set up monitoring and alerting

## Additional Resources

- **Official Documentation**: https://docs.solace.com/
- **Docker Setup Guide**: https://docs.solace.com/Solace-SW-Broker-Set-Up/Docker-Containers/Set-Up-Docker-Container.htm
- **Quick Start Guides**: 
  - Kubernetes: https://github.com/uherbstsolace/solace-kubernetes-quickstart
  - AWS: https://github.com/SolaceProducts/pubsubplus-aws-ha-quickstart
  - Azure: https://learn.microsoft.com/en-us/samples/azure/azure-quickstart-templates/solace-message-router/
  - GCP: https://github.com/SolaceProducts/pubsubplus-gcp-quickstart

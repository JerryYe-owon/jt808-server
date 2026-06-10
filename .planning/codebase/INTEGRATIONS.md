# External Integrations

**Analysis Date:** 2026-06-10

## APIs & External Services

**Message Broker:**
- RabbitMQ - publishes registry/auth/heartbeat/control/response events
  - SDK/Client: Spring AMQP `RabbitTemplate` (`jtt808-server/src/main/java/org/yzh/web/service/MessageProducer.java`, `jtt808-server/src/main/java/org/yzh/web/config/RabbitMQMessageConverterConfig.java`)
  - Auth: `spring.rabbitmq.username`, `spring.rabbitmq.password` (`jtt808-server/src/main/resources/application.yml`, `application-prod.yml`)
  - Exchange/queue/routing topology in `jtt808-server/src/main/java/org/yzh/web/config/RabbitMQConfig.java`

**Device Network Protocols:**
- JT/T 808 over TCP+UDP - device ingress (`jtt808-server/src/main/java/org/yzh/web/config/JTConfig.java`)
- JT/T 1078 and JSATL12 endpoint handling (`jtt808-server/src/main/java/org/yzh/web/endpoint/JT1078Endpoint.java`, `jtt808-server/src/main/java/org/yzh/web/endpoint/JSATL12Endpoint.java`)

**HTTP Service Client (optional):**
- HTTP interface proxy for `/device/{msgId}` APIs
  - SDK/Client: Spring `WebClient` + `HttpServiceProxyFactory` (`jtt808-protocol/src/main/java/org/yzh/protocol/service/JT808ServiceConfiguration.java`)
  - Auth: Not detected
  - Config key: `jt808-service.base-url` (`jtt808-protocol/src/main/java/org/yzh/protocol/service/JT808ServiceProperties.java`)

## Data Storage

**Databases:**
- MariaDB/H2/MyBatis-Plus dependencies are declared (`jtt808-server/pom.xml`)
  - Connection: Not detected in committed `application*.yml`
  - Client: MyBatis-Plus starter (`jtt808-server/pom.xml`)

**File Storage:**
- Local filesystem only
  - Media path: `jt-server.jt808.t0801.path` (`jtt808-server/src/main/resources/application.yml`)
  - Alarm attachment path: `jt-server.jt808.t9208.path` (`jtt808-server/src/main/resources/application.yml`)
  - File write logic: `jtt808-server/src/main/java/org/yzh/web/service/FileService.java`

**Caching:**
- In-process Caffeine caches (`jtt808-server/src/main/java/org/yzh/web/config/BeanConfig.java`, `jtt808-server/src/main/java/org/yzh/web/service/FileService.java`)

## Authentication & Identity

**Auth Provider:**
- Custom device auth over JT/T messages
  - Implementation: register (`T0100`) and auth (`T0102`) handlers populate session/device state (`jtt808-server/src/main/java/org/yzh/web/endpoint/JT808Endpoint.java`)

## Monitoring & Observability

**Error Tracking:**
- External APM service not detected

**Logs:**
- Log4j2 console + rolling files (`jtt808-server/src/main/resources/log4j2.xml`)
- SSE live message stream for browser monitor (`commons/src/main/java/org/yzh/commons/spring/SSEService.java`, `jtt808-server/src/main/resources/static/ws.html`)

## CI/CD & Deployment

**Hosting:**
- Docker image build and runtime (`Dockerfile`)

**CI Pipeline:**
- Not detected (`.github/workflows` not present)

## Environment Configuration

**Required env vars:**
- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`
- `SPRING_PROFILES_ACTIVE`

Evidence: `jtt808-server/src/main/resources/application-prod.yml`, `Dockerfile`.

**Secrets location:**
- Production profile uses env var placeholders (`jtt808-server/src/main/resources/application-prod.yml`)
- Local profile files contain direct broker credentials (`jtt808-server/src/main/resources/application.yml`, `application-dev.yml`, `application-stage.yml`)
- `.env` files: Not detected

## Webhooks & Callbacks

**Incoming:**
- HTTP webhooks: None
- Device callbacks arrive via JT/T socket messages handled in `jtt808-server/src/main/java/org/yzh/web/endpoint/*.java`

**Outgoing:**
- HTTP webhooks: None
- Outgoing async callbacks via RabbitMQ publishes (`jtt808-server/src/main/java/org/yzh/web/service/MessageProducer.java`, `HeartbeatPublisher.java`)

---

*Integration audit: 2026-06-10*

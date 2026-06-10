<!-- refreshed: 2026-06-10 -->
# Architecture

**Analysis Date:** 2026-06-10

## System Overview

```text
┌─────────────────────────────────────────────────────────────┐
│               Ingress + API Facade Layer                    │
├──────────────────┬──────────────────┬───────────────────────┤
│ Netty servers    │ REST controllers │ Protocol endpoints    │
│`jtt808-server/src│`jtt808-server/src│`jtt808-server/src/main│
│/main/java/org/yzh│/main/java/org/yzh│/java/org/yzh/web/     │
│/web/config/JTConf│/web/controller`  │endpoint`              │
│ig.java`          │                  │                       │
└────────┬─────────┴────────┬─────────┴──────────┬────────────┘
         │                  │                     │
         ▼                  ▼                     ▼
┌─────────────────────────────────────────────────────────────┐
│                 Protocol + Session Core                     │
│`jtt808-protocol/src/main/java/org/yzh/protocol/codec`       │
│`jtt808-server/src/main/java/org/yzh/web/endpoint`           │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│              Side Effects / Integrations                    │
│RabbitMQ: `.../web/service/MessageProducer.java`             │
│Files: `.../web/service/FileService.java`                    │
│SSE: `commons/src/main/java/org/yzh/commons/spring/          │
│SSEService.java`                                              │
└─────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| Boot runtime | Starts Spring, scheduling, cache | `jtt808-server/src/main/java/org/yzh/web/JT808Application.java` |
| Transport wiring | Builds TCP/UDP/alarm Netty servers + codec/session beans | `jtt808-server/src/main/java/org/yzh/web/config/JTConfig.java` |
| HTTP command facade | Exposes `/device/*` control APIs | `jtt808-server/src/main/java/org/yzh/web/controller/JT808Controller.java` |
| Uplink handler set | Handles inbound JT messages via `@Endpoint/@Mapping` | `jtt808-server/src/main/java/org/yzh/web/endpoint/JT808Endpoint.java` |
| Command dispatch | Resolves session, sends request/notify, applies timeout | `jtt808-server/src/main/java/org/yzh/web/endpoint/MessageManager.java` |
| Protocol codec | Encode/decode frame, escape/sign, split/reassembly | `jtt808-protocol/src/main/java/org/yzh/protocol/codec/JTMessageEncoder.java` |
| Cross-cutting web behavior | JSON config + API exception mapping + SSE service | `commons/src/main/java/org/yzh/commons/spring/BaseConfig.java`, `ExceptionController.java`, `SSEService.java` |

## Pattern Overview

**Overall:** Layered event-driven gateway (Netty transport + annotation-mapped protocol handlers + Spring REST facade)

**Key Characteristics:**
- Message dispatch is annotation-driven (`@Endpoint`, `@Mapping`) rather than manual switch logic (`jtt808-server/src/main/java/org/yzh/web/endpoint/JT808Endpoint.java`).
- REST controllers delegate to session-aware `MessageManager` (`jtt808-server/src/main/java/org/yzh/web/controller/JT808Controller.java`, `.../endpoint/MessageManager.java`).
- Protocol definitions and codec live in separate reusable module (`jtt808-protocol/src/main/java/org/yzh/protocol`).

## Layers

**Bootstrap & Configuration:**
- Purpose: Start Spring + Netty + MQ + OpenAPI + CORS.
- Location: `jtt808-server/src/main/java/org/yzh/web/config`
- Contains: `JTConfig`, `JTProperties`, `RabbitMQConfig`, `WebMvcConfig`, `SwaggerConfig`.
- Depends on: Spring Boot, NetMC, protocol codec classes.
- Used by: `JT808Application`.

**Transport & Protocol Codec:**
- Purpose: Convert bytes ⇄ message objects and manage subpackage reassembly.
- Location: `jtt808-protocol/src/main/java/org/yzh/protocol/codec`
- Contains: `JTMessageAdapter`, `JTMessageDecoder`, `JTMessageEncoder`, `MultiPacketDecoder`.
- Depends on: Netty `ByteBuf`, Protostar `SchemaManager`.
- Used by: Netty server beans in `JTConfig`.

**Endpoint Handling:**
- Purpose: Process inbound protocol messages and produce responses/side effects.
- Location: `jtt808-server/src/main/java/org/yzh/web/endpoint`
- Contains: `JT808Endpoint`, `JT1078Endpoint`, `JSATL12Endpoint`, `JTHandlerInterceptor`.
- Depends on: session manager, services, protocol DTOs.
- Used by: NetMC `HandlerMapping`.

**Service & Integration:**
- Purpose: Publish MQ events, persist files, push heartbeat and SSE.
- Location: `jtt808-server/src/main/java/org/yzh/web/service`, `commons/src/main/java/org/yzh/commons/spring`
- Contains: `MessageProducer`, `HeartbeatPublisher`, `FileService`, `SSEService`.
- Depends on: RabbitTemplate, filesystem, scheduled tasks, Caffeine.
- Used by: endpoint handlers/controllers.

## Data Flow

### Primary Request Path

1. Netty server bean starts on configured JT ports (`jtt808-server/src/main/java/org/yzh/web/config/JTConfig.java:27`, `:49`).
2. `JTMessagePushAdapter.decodeLog` decodes frame, emits SSE/log, attaches session (`jtt808-server/src/main/java/org/yzh/web/endpoint/JTMessagePushAdapter.java:44`).
3. `@Mapping` method handles typed payload and triggers side effects (example register flow + MQ publish) (`jtt808-server/src/main/java/org/yzh/web/endpoint/JT808Endpoint.java:68`, `:88`).

### HTTP Command to Device

1. Controller receives POST `/device/{msgId}` (`jtt808-server/src/main/java/org/yzh/web/controller/JT808Controller.java:23`).
2. Controller delegates to `MessageManager.request(...)` (`jtt808-server/src/main/java/org/yzh/web/controller/JT808Controller.java:25`, `jtt808-server/src/main/java/org/yzh/web/endpoint/MessageManager.java:38`).
3. Session request interceptor sets clientId/serial/version before encode (`jtt808-server/src/main/java/org/yzh/web/endpoint/JTSessionListener.java:23`).

**State Management:**
- Per-session device identity in session attributes (`jtt808-server/src/main/java/org/yzh/web/model/enums/SessionKey.java`, `JT808Endpoint.java:78`).
- Process-wide heartbeat map in memory (`jtt808-server/src/main/java/org/yzh/web/service/DeviceSessionManager.java:13`).
- In-memory packet assembly maps and file channel cache (`jtt808-protocol/src/main/java/org/yzh/protocol/codec/MultiPacketDecoder.java:21`, `jtt808-server/src/main/java/org/yzh/web/service/FileService.java:77`).

## Key Abstractions

**`JTMessage` hierarchy:**
- Purpose: protocol envelope + typed body.
- Examples: `jtt808-protocol/src/main/java/org/yzh/protocol/basics/JTMessage.java`, `.../t808/T0100.java`, `.../t1078/T9201.java`.
- Pattern: annotation-driven schema mapping resolved by `SchemaManager`.

**Endpoint mapping model:**
- Purpose: bind message IDs to methods.
- Examples: `jtt808-server/src/main/java/org/yzh/web/endpoint/JT808Endpoint.java`.
- Pattern: `@Endpoint` class + `@Mapping(types=...)`.

## Entry Points

**Spring Boot Main:**
- Location: `jtt808-server/src/main/java/org/yzh/web/JT808Application.java`
- Triggers: `main(String[] args)`
- Responsibilities: boot app context and enable scheduling/cache.

**Transport Startup:**
- Location: `jtt808-server/src/main/java/org/yzh/web/config/JTConfig.java`
- Triggers: Spring bean lifecycle (`initMethod="start"`)
- Responsibilities: start TCP/UDP/alarm protocol servers.

## Architectural Constraints

- **Threading:** Netty event-loop processing + daemon background threads for packet timeout and SSE heartbeat (`jtt808-protocol/src/main/java/org/yzh/protocol/codec/MultiPacketDecoder.java:70`, `commons/src/main/java/org/yzh/commons/spring/SSEService.java:105`).
- **Global state:** static ignore-message set in codec adapter (`jtt808-server/src/main/java/org/yzh/web/endpoint/JTMessagePushAdapter.java:22`).
- **Circular imports:** Not detected in scanned `org.yzh.web` and `org.yzh.protocol` packages.
- **Runtime schema package:** message class scan root must match `jt-server.jt808.message-package` (`jtt808-server/src/main/resources/application.yml:39`, `jtt808-server/src/main/java/org/yzh/web/config/JTConfig.java:108`).

## Anti-Patterns

### Empty mapped handlers

**What happens:** Several `@Mapping` methods are no-op stubs.
**Why it's wrong:** Message types are accepted but produce no business output, making behavior implicit.
**Do this instead:** Follow response/publish patterns used by handled messages (for example `T0104` and `T0200`) in `jtt808-server/src/main/java/org/yzh/web/endpoint/JT808Endpoint.java:123`, `:149`.

### Mixed infra responsibilities in endpoint

**What happens:** protocol handlers perform transport response, MQ mapping, and file orchestration directly.
**Why it's wrong:** High coupling in `JT808Endpoint` increases change risk.
**Do this instead:** Keep handler thin and delegate to service classes (`jtt808-server/src/main/java/org/yzh/web/service/*`) as done for `FileService` and `MessageProducer`.

## Error Handling

**Strategy:** protocol ACK/NACK for device traffic + structured API error bodies for HTTP.

**Patterns:**
- Unsupported/exceptions become JT `T0001` responses in interceptor (`jtt808-server/src/main/java/org/yzh/web/endpoint/JTHandlerInterceptor.java:21`, `:54`).
- HTTP exceptions normalize through `@RestControllerAdvice` and `R/APIException` (`commons/src/main/java/org/yzh/commons/spring/ExceptionController.java`).

## Cross-Cutting Concerns

**Logging:** Log4j2 rolling files + per-message encode/decode logs (`jtt808-server/src/main/resources/log4j2.xml`, `jtt808-server/src/main/java/org/yzh/web/endpoint/JTMessagePushAdapter.java`).
**Validation:** property constraints and channel validation in config objects (`jtt808-server/src/main/java/org/yzh/web/config/JTProperties.java`, `jtt808-server/src/main/java/org/yzh/web/model/enums/Video360Mode.java`).
**Authentication:** device registration/auth in protocol handlers (`jtt808-server/src/main/java/org/yzh/web/endpoint/JT808Endpoint.java:68`, `:97`); Spring Security not detected.

---

*Architecture analysis: 2026-06-10*

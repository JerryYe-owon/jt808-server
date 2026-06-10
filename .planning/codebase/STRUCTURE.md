# Codebase Structure

**Analysis Date:** 2026-06-10

## Directory Layout

```text
jt808-server/
├── commons/                 # Shared API model, Spring base config, SSE, utility code
├── jtt808-protocol/         # Protocol message models, codec, optional HTTP client API
├── jtt808-server/           # Gateway runtime app (Netty transport + REST + MQ/file services)
├── docs/                    # Documentation assets
├── 协议文档/                 # Protocol docs and packet testing tools
├── .planning/codebase/      # GSD mapping documents
├── logs/                    # Runtime log output directory
└── pom.xml                  # Maven parent aggregator
```

## Directory Purposes

**`commons/src/main/java/org/yzh/commons/model`:**
- Purpose: common API response and exception contracts.
- Contains: `R`, `APIException`, `APICodes`.
- Key files: `commons/src/main/java/org/yzh/commons/model/R.java`

**`commons/src/main/java/org/yzh/commons/spring`:**
- Purpose: reusable Spring infrastructure (JSON config, exception advice, SSE).
- Contains: `BaseConfig`, `ExceptionController`, `SSEService`.
- Key files: `commons/src/main/java/org/yzh/commons/spring/BaseConfig.java`

**`jtt808-protocol/src/main/java/org/yzh/protocol/codec`:**
- Purpose: frame encode/decode, signing/escaping, subpackage reassembly.
- Contains: `JTMessageAdapter`, `JTMessageDecoder`, `JTMessageEncoder`, `MultiPacketDecoder`.
- Key files: `jtt808-protocol/src/main/java/org/yzh/protocol/codec/JTMessageAdapter.java`

**`jtt808-protocol/src/main/java/org/yzh/protocol/t808|t1078|jsatl12`:**
- Purpose: protocol DTO/message definitions grouped by standard.
- Contains: classes like `T0100`, `T9201`, `T1210`.
- Key files: `jtt808-protocol/src/main/java/org/yzh/protocol/t808/T0200.java`

**`jtt808-server/src/main/java/org/yzh/web/config`:**
- Purpose: runtime wiring (transport, properties, MQ, swagger, CORS, cache).
- Contains: `JTConfig`, `JTProperties`, `RabbitMQConfig`, `WebMvcConfig`, `BeanConfig`.
- Key files: `jtt808-server/src/main/java/org/yzh/web/config/JTConfig.java`

**`jtt808-server/src/main/java/org/yzh/web/controller`:**
- Purpose: HTTP API endpoints for downlink commands and operational utilities.
- Contains: `JT808Controller`, `JT1078Controller`, `OtherController`.
- Key files: `jtt808-server/src/main/java/org/yzh/web/controller/JT808Controller.java`

**`jtt808-server/src/main/java/org/yzh/web/endpoint`:**
- Purpose: inbound device message handlers and session/interceptor adapters.
- Contains: `JT808Endpoint`, `JT1078Endpoint`, `JSATL12Endpoint`, `JTHandlerInterceptor`, `JTSessionListener`.
- Key files: `jtt808-server/src/main/java/org/yzh/web/endpoint/JT808Endpoint.java`

**`jtt808-server/src/main/java/org/yzh/web/service`:**
- Purpose: integration and state services.
- Contains: `MessageProducer`, `HeartbeatPublisher`, `FileService`, `DeviceSessionManager`.
- Key files: `jtt808-server/src/main/java/org/yzh/web/service/MessageProducer.java`

## Key File Locations

**Entry Points:**
- `jtt808-server/src/main/java/org/yzh/web/JT808Application.java`: Spring Boot main class.
- `jtt808-server/src/main/java/org/yzh/web/config/JTConfig.java`: transport server bean startup.

**Configuration:**
- `pom.xml`: parent dependency/version/build management.
- `jtt808-server/src/main/resources/application.yml`: base runtime config.
- `jtt808-server/src/main/resources/application-dev.yml`: dev profile overrides.
- `jtt808-server/src/main/resources/application-stage.yml`: stage profile overrides.
- `jtt808-server/src/main/resources/application-prod.yml`: prod env-var based overrides.
- `jtt808-server/src/main/resources/log4j2.xml`: logging appenders and rotation.
- `Dockerfile`: build and deploy container recipe.

**Core Logic:**
- `jtt808-protocol/src/main/java/org/yzh/protocol/codec`: protocol codec core.
- `jtt808-server/src/main/java/org/yzh/web/endpoint`: inbound message business handling.
- `jtt808-server/src/main/java/org/yzh/web/endpoint/MessageManager.java`: outbound request/notify bridge.

**Testing:**
- `jtt808-protocol/src/test/java/org/yzh/protocol`: protocol-focused tests.
- `jtt808-server/src/test/java/org/yzh/web`: web/config tests.
- `jtt808-server/src/test/java/org/yzh/client`: simulated client/stress tests.

## Naming Conventions

**Files:**
- Java classes use UpperCamelCase (`JTConfig.java`, `MessageProducer.java`).
- Protocol message classes use `Txxxx` identifiers (`T0100.java`, `T9208.java`).
- Config classes use suffixes like `*Config` and `*Properties` (`RabbitMQConfig.java`, `JTProperties.java`).

**Directories:**
- Server module follows layer packages (`config`, `controller`, `endpoint`, `service`, `model`).
- Protocol module follows domain/protocol packages (`codec`, `basics`, `t808`, `t1078`, `jsatl12`, `service`).

## Where to Add New Code

**New Feature:**
- Primary code: inbound behavior in `jtt808-server/src/main/java/org/yzh/web/endpoint`; outbound API in `jtt808-server/src/main/java/org/yzh/web/controller`.
- Tests: `jtt808-server/src/test/java` for server behavior; `jtt808-protocol/src/test/java` for codec/model behavior.

**New Component/Module:**
- Reusable protocol/model/codec logic: `jtt808-protocol/src/main/java/org/yzh/protocol`.
- Runtime wiring and infrastructure beans: `jtt808-server/src/main/java/org/yzh/web/config`.

**Utilities:**
- Shared utils: `commons/src/main/java/org/yzh/commons/util`.
- Shared response/error abstractions: `commons/src/main/java/org/yzh/commons/model`.

## Special Directories

**`logs/`:**
- Purpose: runtime log output path.
- Generated: Yes.
- Committed: No (runtime artifact directory).

**`target/` (module-local):**
- Purpose: Maven build outputs.
- Generated: Yes.
- Committed: No.

**`.planning/codebase/`:**
- Purpose: planning/mapping docs for GSD workflows.
- Generated: Yes.
- Committed: Project-dependent (present in this repo).

**`jtt808-server/src/main/resources/static`:**
- Purpose: static monitoring page assets (`ws.html`).
- Generated: No.
- Committed: Yes.

---

*Structure analysis: 2026-06-10*

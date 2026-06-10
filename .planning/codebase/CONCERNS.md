# Codebase Concerns

**Analysis Date:** 2026-06-10

## Tech Debt

**Error handling utilities suppress failures globally:**
- Issue: Multiple utility methods swallow exceptions and return `null`/no-op, which hides operational faults.
- Files: `commons/src/main/java/org/yzh/commons/util/Exceptions.java`, `commons/src/main/java/org/yzh/commons/util/IOUtils.java`, `commons/src/main/java/org/yzh/commons/util/DateUtils.java`, `commons/src/main/java/org/yzh/commons/util/JsonUtils.java`
- Impact: Parse/write failures become silent; downstream `NullPointerException` and data loss are harder to diagnose.
- Fix approach: Replace generic `ignore` patterns with typed error handling and structured logging at call sites; avoid returning `null` for parse failures.

**Large endpoint/controller classes centralize too many responsibilities:**
- Issue: Protocol handling and transport publishing are concentrated in large classes.
- Files: `jtt808-server/src/main/java/org/yzh/web/endpoint/JT808Endpoint.java` (215 lines), `jtt808-server/src/main/java/org/yzh/web/controller/JT808Controller.java` (203 lines), `jtt808-server/src/main/java/org/yzh/web/service/FileService.java` (209 lines)
- Impact: Change risk is high; protocol updates and behavior changes are tightly coupled.
- Fix approach: Split handlers by protocol message domain (registration, telemetry, media, query/command) and isolate storage logic behind dedicated services.

**Tests are disabled by default in Maven build:**
- Issue: Parent POM sets tests to skip globally.
- Files: `pom.xml` (`<maven.test.skip>true</maven.test.skip>`)
- Impact: Regressions can ship without automated detection.
- Fix approach: Remove global skip and gate skips to explicit CI/dev profiles only.

## Known Bugs

**Multipart retry serial number is never captured:**
- Symptoms:补传请求 may use invalid response serial number.
- Files: `jtt808-protocol/src/main/java/org/yzh/protocol/codec/MultiPacket.java` (setter uses `if (serialNo == -1)`), `jtt808-server/src/main/java/org/yzh/web/endpoint/JTMultiPacketListener.java` (`request.setResponseSerialNo(multiPacket.getSerialNo())`)
- Trigger: Any fragmented message requiring timeout retry.
- Workaround: Not detected.

**Potential NPE in alarm attachment completion path:**
- Symptoms: `alarmFileComplete` dereferences cached metadata without null guards.
- Files: `jtt808-server/src/main/java/org/yzh/web/endpoint/JSATL12Endpoint.java` (`fileInfos.get(name)` then `fileInfo.getParent()`)
- Trigger: Completion packet arrives after cache expiration or missing prior metadata packet.
- Workaround: Not detected.

## Security Considerations

**Unauthenticated control APIs and monitoring endpoints:**
- Risk: Remote callers can issue terminal commands, send raw packets, list sessions, and change log levels.
- Files: `jtt808-server/src/main/java/org/yzh/web/controller/JT808Controller.java`, `jtt808-server/src/main/java/org/yzh/web/controller/JT1078Controller.java`, `jtt808-server/src/main/java/org/yzh/web/controller/OtherController.java`, `jtt808-server/pom.xml` (no Spring Security dependency)
- Current mitigation: Not detected.
- Recommendations: Add authentication/authorization filter chain, restrict sensitive endpoints (`/device/raw`, `/logger`, `/device/*`) by role, and enforce audit logging.

**Overly permissive CORS with credentials enabled:**
- Risk: Any origin can send credentialed browser requests.
- Files: `jtt808-server/src/main/java/org/yzh/web/config/WebMvcConfig.java` (`allowedOriginPattern=*`, all methods/headers, `allowCredentials=true`)
- Current mitigation: Not detected.
- Recommendations: Whitelist trusted origins and disable credentials for wildcard CORS.

**Sensitive internals are exposed to clients:**
- Risk: Full server stack traces are returned in API error payloads.
- Files: `commons/src/main/java/org/yzh/commons/spring/BaseConfig.java` (`details` includes stack trace), `jtt808-server/src/main/java/org/yzh/web/endpoint/MessageManager.java` (returns stack trace in `APIException`)
- Current mitigation: Not detected.
- Recommendations: Return stable error codes/messages externally; keep stack traces in logs only.

**Plaintext credentials and defaults committed in config:**
- Risk: Credentials/default secrets exist in versioned config and can leak operational access.
- Files: `jtt808-server/src/main/resources/application.yml`, `jtt808-server/src/main/resources/application-dev.yml`, `jtt808-server/src/main/resources/application-stage.yml`, `jtt808-server/src/main/resources/application-prod.yml`
- Current mitigation: Partial env substitution in `application-stage.yml`; direct plaintext still present in profile files.
- Recommendations: Move secrets to environment/secret manager and remove committed plaintext credentials.

**Path traversal risk during file upload handling:**
- Risk: Device-provided file names are concatenated into filesystem paths without normalization.
- Files: `jtt808-server/src/main/java/org/yzh/web/service/FileService.java` (`dir + name`, `Paths.get(dir + name)`, rename/delete on derived paths)
- Current mitigation: Not detected.
- Recommendations: Normalize and validate file names (`Path.normalize`), reject path separators/parent traversal, and confine writes to fixed root.

## Performance Bottlenecks

**High-volume message logging includes full JSON + hex payload:**
- Problem: Per-message serialization and hex dump generation adds CPU, memory, and I/O pressure.
- Files: `jtt808-server/src/main/java/org/yzh/web/endpoint/JTMessagePushAdapter.java`
- Cause: Every decode/encode path builds full payload strings and logs at `info`.
- Improvement path: Reduce default log level, sample high-frequency message types, and avoid hex dump unless debug is enabled.

**Per-device heartbeat publishing scales linearly with online device count:**
- Problem: Full map scan and RabbitMQ publish every 30 seconds can become expensive at high cardinality.
- Files: `jtt808-server/src/main/java/org/yzh/web/service/HeartbeatPublisher.java`, `jtt808-server/src/main/java/org/yzh/web/service/DeviceSessionManager.java`
- Cause: In-memory map iteration + one message per device at fixed schedule.
- Improvement path: Publish only changed state or batch by partition; keep bounded device state lifecycle.

## Fragile Areas

**Multipart decode state management:**
- Files: `jtt808-protocol/src/main/java/org/yzh/protocol/codec/MultiPacketDecoder.java`, `jtt808-protocol/src/main/java/org/yzh/protocol/codec/MultiPacket.java`
- Why fragile: Timeout/retry logic depends on mutable in-memory packet state and background thread timing.
- Safe modification: Preserve packet lifecycle invariants (add, retry, release) and add concurrency tests around timeout/retry paths.
- Test coverage: No dedicated integration test observed for retry timeout and补传 serial number correctness.

**Attachment file assembly pipeline:**
- Files: `jtt808-server/src/main/java/org/yzh/web/endpoint/JSATL12Endpoint.java`, `jtt808-server/src/main/java/org/yzh/web/service/FileService.java`
- Why fragile: Multiple steps (cache, chunk write, log, integrity check, rename) can fail independently; many branches return `null`.
- Safe modification: Add explicit state transitions and null-safe checks before persistence operations.
- Test coverage: No module tests observed for cache expiry and out-of-order chunk completion handling.

## Scaling Limits

**Fixed async batch worker size for GPS ingestion:**
- Current capacity: `@AsyncBatch(poolSize = 2, maxElements = 4000, maxWait = 1000)` in telemetry path.
- Limit: Throughput is bounded by two workers; queueing latency grows during spikes.
- Scaling path: Externalize pool/batch settings to config and tune based on CPU/core and broker throughput.

**Frame size caps constrain larger payload scenarios:**
- Current capacity: Netty frame length is fixed to `2 + 21 + 1023 * 2 + 1 + 2`.
- Limit: Larger protocol payloads risk rejection or fragmentation complexity.
- Scaling path: Make max frame length configurable and validate against target terminal payload distributions.

## Dependencies at Risk

**High JDK baseline mismatch with project docs/ecosystem:**
- Risk: Build requires Java 25 while README indicates JDK 17+, increasing deployment friction.
- Impact: Teams on LTS runtimes may fail to build/run consistently.
- Migration plan: Align declared Java target with supported runtime policy (prefer LTS) and document exact tested matrix.

## Missing Critical Features

**No API access control boundary for administrative/device-control operations:**
- Problem: Critical operational endpoints are exposed without authN/authZ checks.
- Blocks: Safe multi-tenant deployment and secure internet-facing operation.

## Test Coverage Gaps

**Server-side controller/endpoint behavior is largely untested by automated tests:**
- What's not tested: Access control, error response behavior, multipart retry correctness, attachment upload failure handling.
- Files: `jtt808-server/src/main/java/org/yzh/web/controller/*.java`, `jtt808-server/src/main/java/org/yzh/web/endpoint/*.java`, `jtt808-server/src/main/java/org/yzh/web/service/FileService.java`
- Risk: Regressions in control flows and edge cases can reach production undetected.
- Priority: High

---

*Concerns audit: 2026-06-10*

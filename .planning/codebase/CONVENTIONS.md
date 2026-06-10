# Coding Conventions

**Analysis Date:** 2026-06-10

## Naming Patterns

**Files:**
- Java source files use `PascalCase.java` (for example, `jtt808-server/src/main/java/org/yzh/web/controller/JT808Controller.java`, `jtt808-protocol/src/main/java/org/yzh/protocol/t808/T0100.java`).
- Tests generally use `*Test.java` (for example, `jtt808-server/src/test/java/org/yzh/web/config/JTPropertiesVideo360Test.java`, `jtt808-protocol/src/test/java/org/yzh/protocol/JT808BeansTest.java`), with some test-scope harness utilities not using `*Test` (for example, `jtt808-server/src/test/java/org/yzh/QuickStart.java`).

**Functions:**
- Methods use `camelCase` in application/common code (`jtt808-server/src/main/java/org/yzh/web/endpoint/MessageManager.java`, `commons/src/main/java/org/yzh/commons/model/R.java`).
- Protocol handler methods are intentionally named by message ID (for example, `T0100`, `T0200` in `jtt808-server/src/main/java/org/yzh/web/endpoint/JT808Endpoint.java`).

**Variables:**
- Fields and locals use `camelCase` (`jtt808-server/src/main/java/org/yzh/web/config/JTProperties.java`).
- Constants use `UPPER_SNAKE_CASE` (`jtt808-protocol/src/test/java/org/yzh/protocol/JT808Beans.java`).

**Types:**
- Classes/interfaces use `PascalCase` under `org.yzh.*` packages (`AGENTS.md`, `jtt808-server/src/main/java/org/yzh/web/JT808Application.java`).
- Nested config types use `C` prefixes in `JTProperties` (`jtt808-server/src/main/java/org/yzh/web/config/JTProperties.java`).

## Code Style

**Formatting:**
- Tool used: Not detected (no formatter config such as `.editorconfig`/Checkstyle/Spotless detected in repo root).
- Key settings: 4-space indentation and package/class/member layout are followed consistently (`AGENTS.md`, `jtt808-server/src/main/java/org/yzh/web/controller/JT808Controller.java`).

**Linting:**
- Tool used: Not detected (no Checkstyle/PMD/SpotBugs configuration files found).
- Key rules: Convention-driven only; no enforced lint rule set detected in `pom.xml` files.

## Import Organization

**Order:**
1. Normal imports grouped by package (`java.*`, third-party, project-local) (for example, `jtt808-server/src/main/java/org/yzh/web/endpoint/JT808Endpoint.java`).
2. Static imports at the bottom in a separate group (for example, `jtt808-protocol/src/test/java/org/yzh/protocol/JT808BeansTest.java`).
3. Wildcard imports are used for dense protocol type sets (for example, `org.yzh.protocol.t808.*` in `jtt808-server/src/main/java/org/yzh/web/controller/JT808Controller.java`).

**Path Aliases:**
- Not applicable (Java package imports only).

## Error Handling

**Patterns:**
- Centralized HTTP exception translation via `@RestControllerAdvice` (`commons/src/main/java/org/yzh/commons/spring/ExceptionController.java`).
- Reactive request timeout and wrapped API errors in `MessageManager` (`jtt808-server/src/main/java/org/yzh/web/endpoint/MessageManager.java`).
- `IllegalArgumentException` is used for configuration validation and tested directly (`jtt808-server/src/main/java/org/yzh/web/config/JTProperties.java`, `jtt808-server/src/test/java/org/yzh/web/config/JTPropertiesVideo360Test.java`).

## Logging

**Framework:** SLF4J with Log4j2 backend (`jtt808-server/pom.xml`, `jtt808-server/src/main/resources/log4j2.xml`).

**Patterns:**
- Prefer `@Slf4j` on components and controllers (`jtt808-server/src/main/java/org/yzh/web/JT808Application.java`, `commons/src/main/java/org/yzh/commons/spring/ExceptionController.java`).
- Use `warn` for handled exceptions and `info` for startup milestones (`commons/src/main/java/org/yzh/commons/spring/ExceptionController.java`, `jtt808-server/src/main/java/org/yzh/web/JT808Application.java`).

## Comments

**When to Comment:**
- Javadoc used on many protocol and utility classes with author/source links (`jtt808-protocol/src/main/java/org/yzh/protocol/t808/T0100.java`, `jtt808-protocol/src/test/java/org/yzh/protocol/JT808BeansTest.java`).
- Inline comments used for protocol semantics and operational hints (`jtt808-server/src/main/java/org/yzh/web/endpoint/JT808Endpoint.java`, `jtt808-server/src/main/java/org/yzh/web/config/JTProperties.java`).

**JSDoc/TSDoc:**
- Not applicable. JavaDoc is used instead.

## Function Design

**Size:** Endpoint/controller methods are usually short delegators; shared behavior is centralized in helper/service methods (`jtt808-server/src/main/java/org/yzh/web/controller/JT808Controller.java`, `jtt808-server/src/main/java/org/yzh/web/endpoint/MessageManager.java`).

**Parameters:** DTO/message objects are passed directly, commonly with Spring `@RequestBody` or endpoint mapping signatures (`jtt808-server/src/main/java/org/yzh/web/controller/JT808Controller.java`, `jtt808-server/src/main/java/org/yzh/web/endpoint/JT808Endpoint.java`).

**Return Values:** Fluent chained setters are common via Lombok `@Accessors(chain = true)`, and reactive APIs return `Mono<T>` in web commands (`jtt808-protocol/src/main/java/org/yzh/protocol/t808/T0100.java`, `jtt808-server/src/main/java/org/yzh/web/controller/JT808Controller.java`).

## Module Design

**Exports:** Public classes expose module APIs by package (for example, `org.yzh.protocol.service.JT808Service`, `org.yzh.commons.model.R`).

**Barrel Files:** Not applicable in Java modules.

---

*Convention analysis: 2026-06-10*

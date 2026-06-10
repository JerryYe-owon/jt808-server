# Testing Patterns

**Analysis Date:** 2026-06-10

## Test Framework

**Runner:**
- JUnit 5 (Jupiter) via Maven dependencies in `jtt808-protocol/pom.xml` (`org.junit.jupiter:junit-jupiter`) and `jtt808-server/pom.xml` (`spring-boot-starter-test`).
- Config: No dedicated `junit-platform.properties`/Surefire custom config detected; tests rely on Maven defaults from `pom.xml` files.

**Assertion Library:**
- JUnit Jupiter assertions (`assertEquals`, `assertThrows`) in `jtt808-server/src/test/java/org/yzh/web/config/JTPropertiesVideo360Test.java` and `jtt808-protocol/src/test/java/org/yzh/protocol/BeanTest.java`.

**Run Commands:**
```bash
mvn test                                  # Run all tests (requires overriding skip flag in parent if needed)
mvn -Dmaven.test.skip=false test          # Force-enable tests; parent `pom.xml` sets maven.test.skip=true
mvn -pl jtt808-protocol -am test          # Run protocol module tests
```

## Test File Organization

**Location:**
- Module-local test sources under `*/src/test/java` and resources under `*/src/test/resources` (for example, `jtt808-protocol/src/test/java`, `jtt808-server/src/test/resources`).

**Naming:**
- Primary pattern: `*Test.java` (`jtt808-protocol/src/test/java/org/yzh/protocol/JT808BeansTest.java`, `jtt808-server/src/test/java/org/yzh/web/controller/JT1078ControllerVideo360Test.java`).
- Auxiliary test-scope runners/harnesses can use descriptive names without `@Test` methods (`jtt808-server/src/test/java/org/yzh/QuickStart.java`, `jtt808-server/src/test/java/org/yzh/client/StressTest.java`).

**Structure:**
```
commons/src/test/resources/
jtt808-protocol/src/test/java/org/yzh/protocol/
jtt808-protocol/src/test/resources/
jtt808-server/src/test/java/org/yzh/{client,web,...}/
jtt808-server/src/test/resources/
```

## Test Structure

**Suite Organization:**
```java
class JT1078ControllerVideo360Test {
    private final MessageManager messageManager = mock(MessageManager.class);
    private JT1078Controller controller;

    @BeforeEach
    void setUp() { ... }

    @Test
    void start360StreamOverridesChannelAndDefaultsMissingMediaType() throws Exception { ... }
}
```
- Pattern source: `jtt808-server/src/test/java/org/yzh/web/controller/JT1078ControllerVideo360Test.java`

**Patterns:**
- Setup pattern: explicit `@BeforeEach` constructor wiring (`jtt808-server/src/test/java/org/yzh/web/controller/JT1078ControllerVideo360Test.java`).
- Teardown pattern: Not commonly needed; tests rely on method-local objects.
- Assertion pattern: JUnit assertions + behavioral verify (`assertEquals`, `assertThrows`, `verify`) in `jtt808-server/src/test/java/org/yzh/web/config/JTPropertiesVideo360Test.java` and `jtt808-server/src/test/java/org/yzh/web/controller/JT1078ControllerVideo360Test.java`.

## Mocking

**Framework:** Mockito (from `spring-boot-starter-test`), using `mock`, `when`, `verify`, and `ArgumentCaptor`.

**Patterns:**
```java
when(messageManager.request(any(T9101.class), eq(T0001.class)))
        .thenReturn(Mono.just(response));
verify(messageManager).request(captor.capture(), eq(T0001.class));
```
- Pattern source: `jtt808-server/src/test/java/org/yzh/web/controller/JT1078ControllerVideo360Test.java`

**What to Mock:**
- Outbound collaborators such as `MessageManager` in controller tests (`jtt808-server/src/test/java/org/yzh/web/controller/JT1078ControllerVideo360Test.java`).

**What NOT to Mock:**
- Protocol codec/serialization path in round-trip tests; these tests exercise real encoder/decoder (`jtt808-protocol/src/test/java/org/yzh/protocol/BeanTest.java`, `jtt808-protocol/src/test/java/org/yzh/protocol/JT808BeansTest.java`).

## Fixtures and Factories

**Test Data:**
```java
public static <T extends JTMessage> T H2019(T message) {
    message.setProtocolVersion(1);
    message.setClientId("12345678901234567890");
    message.setSerialNo(65535);
    return message;
}
```
- Fixture source: `jtt808-protocol/src/test/java/org/yzh/protocol/JT808Beans.java`

**Location:**
- In-code factories: `jtt808-protocol/src/test/java/org/yzh/protocol/JT808Beans.java`
- Hex/resource fixtures: `jtt808-protocol/src/test/resources/JT808.txt`, `jtt808-protocol/src/test/resources/JT1078.txt`, `jtt808-server/src/test/resources/轨迹区域测试.txt`

## Coverage

**Requirements:** None enforced (no JaCoCo or minimum coverage rules detected in `pom.xml` files).

**View Coverage:**
```bash
Not detected
```

## Test Types

**Unit Tests:**
- Protocol encode/decode round-trip tests (`jtt808-protocol/src/test/java/org/yzh/protocol/JT808BeansTest.java`, `jtt808-protocol/src/test/java/org/yzh/protocol/TestHex.java`).
- Property binding and controller behavior tests (`jtt808-server/src/test/java/org/yzh/web/config/JTPropertiesVideo360Test.java`, `jtt808-server/src/test/java/org/yzh/web/controller/JT1078ControllerVideo360Test.java`).

**Integration Tests:**
- Manual/integration runners under test scope with `main` entry points (`jtt808-server/src/test/java/org/yzh/JT808ServiceTest.java`, `jtt808-server/src/test/java/org/yzh/client/ClientTest.java`, `jtt808-server/src/test/java/org/yzh/client/StressTest.java`).

**E2E Tests:**
- Not used as an automated suite; operational verification uses manual client/harness flows (`README.md`, `jtt808-server/src/test/java/org/yzh/client/ClientTest.java`).

## Common Patterns

**Async Testing:**
```java
T0001 actual = controller.T9101Video360("left", body).block();
```
- Pattern source: `jtt808-server/src/test/java/org/yzh/web/controller/JT1078ControllerVideo360Test.java`

**Error Testing:**
```java
assertThrows(IllegalArgumentException.class, () -> video360.setChannels(channels));
```
- Pattern source: `jtt808-server/src/test/java/org/yzh/web/config/JTPropertiesVideo360Test.java`

---

*Testing analysis: 2026-06-10*
